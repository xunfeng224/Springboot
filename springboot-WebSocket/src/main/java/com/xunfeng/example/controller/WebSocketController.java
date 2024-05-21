package com.xunfeng.example.controller;


import com.xunfeng.example.websocket.SocketMessage;
import com.xunfeng.example.websocket.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/websocket")
public class WebSocketController {

    @Autowired
    private WebSocket webSocket;

    @GetMapping("/sendMessage")
    public Boolean sendMessage(String topic) {
        SocketMessage message = new SocketMessage();
        SocketMessage.Header header = new SocketMessage.Header();
        header.setMessageType("type1");
        header.setTopic(topic);
        message.setHeader(header);
        message.setBody("business data");
        webSocket.sendMessageByTopic(topic, message);
        return Boolean.TRUE;
    }
}