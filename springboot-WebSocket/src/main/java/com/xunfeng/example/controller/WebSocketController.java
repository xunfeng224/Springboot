package com.xunfeng.example.controller;


import com.xunfeng.example.websocket.SocketMessage;
import com.xunfeng.example.websocket.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试接口
 */
@RestController
@RequestMapping("/websocketTest")
public class WebSocketController {

    @Autowired
    private WebSocket webSocket;

    /**
     * 通过Topic发送信息
     *
     * @param topic
     * @param body
     */
    @GetMapping("/topic/sendMessage")
    public void sendMessageByTopic(String topic, String body) {
        SocketMessage message = new SocketMessage();
        SocketMessage.Header header = new SocketMessage.Header();
        header.setMessageType("type1");
        header.setTopic(topic);
        message.setHeader(header);
        message.setBody(body);
        webSocket.sendMessageByTopic(topic, message);
    }

    /**
     * 通过UserId发送信息
     *
     * @param userId
     * @param message
     */
    @GetMapping("/userId/sendMessage")
    public void sendMessageByUserId(String userId, String message) {
        webSocket.sendOneMessage(userId, message);
    }
}