package com.fir.netty.controller;

import com.fir.netty.server.netty.ChannelMap;
import com.fir.netty.server.netty.server.NettyServerHandler;
import io.netty.buffer.ByteBuf;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.netty.channel.Channel;


/**
 * @author fir
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class MsgController {


    /**
     * 向一个客户端发送消息
     *
     * @param id 客户端编号
     * @param msg 消息
     */
    @GetMapping("/send")
    public String send(String id, String msg){

        // 客户端ID
        Channel channel = ChannelMap.getChannelByName(id);
        if (null == channel) {
            throw new RuntimeException("客户端已离线");
        }
        ByteBuf buffer = NettyServerHandler.writeInfo(msg);
        channel.writeAndFlush(buffer);
        return null;
    }

}
