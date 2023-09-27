package com.lung.game.service.impl;

import com.lung.server.memory.User;
import com.lung.game.entry.proto.msg.MsgPlayer;
import com.lung.game.service.PlayerService;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@Component
public class PlayerServiceImpl implements PlayerService {

    @Override
    public void login(User user, MsgPlayer.CSLogin msg) {
        System.out.println("你好");
    }
}
