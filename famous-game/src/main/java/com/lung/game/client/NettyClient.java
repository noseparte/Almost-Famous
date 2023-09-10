package com.lung.game.client;

import com.lung.game.bean.proto.msg.MsgPlayer;
import com.lung.utils.CommonUtils;
import com.lung.utils.SslUtils;
import com.lung.utils.TraceUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author haoyitao
 * @version 1.0
 * @implSpec
 * @since 2023/9/4 - 17:37
 */
public class NettyClient {

    private final static Logger logger = LoggerFactory.getLogger(NettyClient.class);

    private final URI uri;
    private final EventLoopGroup group;

    static Channel channel;

    public NettyClient(URI uri) {
        this.uri = uri;
        this.group = new NioEventLoopGroup();
    }


    public void connect() throws Exception {
        try {
            SslContext sslContext = SslUtils.createClientSslContext();

            // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
            // If you change it to V00, ping is not supported and remember to change
            // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
            final WebSocketClientHandler handler =
                    new WebSocketClientHandler(
                            WebSocketClientHandshakerFactory.newHandshaker(
                                    uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders()));

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.TCP_NODELAY, true)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(sslContext.newHandler(ch.alloc(), uri.getHost(), uri.getPort()));
                            p.addLast(
                                    new HttpClientCodec(),
                                    new HttpObjectAggregator(8192),
                                    WebSocketClientCompressionHandler.INSTANCE,
//                                    new IdleStateHandler(5, 5, 30, TimeUnit.SECONDS),
                                    handler);
                        }

                    })
            ;

            channel = b.connect(uri.getHost(), uri.getPort()).sync().channel();
            handler.handshakeFuture().sync();
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String msg = console.readLine();
                if (msg == null) {
                    break;
                } else if ("bye".equals(msg.toLowerCase())) {
                    channel.writeAndFlush(new CloseWebSocketFrame());
                    channel.closeFuture().sync();
                    break;
                } else if ("ping".equals(msg.toLowerCase())) {
                    WebSocketFrame frame = new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[] { 8, 1, 8, 1 }));
                    channel.writeAndFlush(frame);
                } else {
                    WebSocketFrame frame = new TextWebSocketFrame(msg);
                    channel.writeAndFlush(frame);
                }
            }
//            channel.closeFuture().sync();
        } catch (Exception e) {
            String traceInfo = TraceUtils.getTraceInfo(e);
            logger.error("run error, {}", traceInfo, e);
            throw new RuntimeException(e);
        } finally {
            group.shutdownGracefully();
        }
        logger.info("client connecting server successful, {}", uri);
    }

    public static void main(String[] args) throws Exception {
        URI uri = new URI("ws://localhost:8888/ws"); // 设置 WebSocket 服务器的地址
        NettyClient client = new NettyClient(uri);
        client.connect();

        channel.writeAndFlush(new PingWebSocketFrame());

        // 在握手成功之后立即发送消息给服务器
        MsgPlayer.CSLogin.Builder loginMessage = MsgPlayer.CSLogin.newBuilder();
        loginMessage.setUid("123456");
        channel.writeAndFlush(loginMessage.build());
    }

}
