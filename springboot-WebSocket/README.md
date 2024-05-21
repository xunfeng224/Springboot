

[toc]



# Springboot整合WebSocket

本文实现如何将WebSocket在SpringBoot中整合并且使用，SpringBoot中WebSocket组件已经非常完善，使用起来并不困难。

WebSocket使用方式有很多，这里主要展示常用的两种：

1. 点对点式，通过userId进行消息的收发
2. 订阅式，通过topic进行消息订阅、取消订阅、消息群发

## 引入
> WebSocket 是一种基于 TCP 协议的全双工通信协议，它允许客户端和服务器之间建立持久的、双向的通信连接。相比传统的 HTTP 请求 - 响应模式，WebSocket 提供了实时、低延迟的数据传输能力。通过 WebSocket，客户端和服务器可以在任意时间点互相发送消息，实现实时更新和即时通信的功能。WebSocket 协议经过了多个浏览器和服务器的支持，成为了现代 Web 应用中常用的通信协议之一。它广泛应用于聊天应用、实时数据更新、多人游戏等场景，为 Web 应用提供了更好的用户体验和更高效的数据传输方式。

## 项目结构





## 整合流程

### 添加依赖

在 `pom.xml` 中添加 `spring-boot-starter-websocket` 依赖。

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
```



###  创建WebSocket操作类

此类为WebSocket服务端端点，WebSocket功能实现及处理都在此类中完成。
注解@ServerEndpoint("/websocket/{userId}")中"/websocket/{userId}"为ws链接url，其中userId参数用于点对点式

该类方法虽然多，但是都很简单，主要原理为通过ConcurrentHashMap保存客户端连接信息，并使用Session对客户端进行消息发送

可分为三大部分：

1. @OnMessage、@OnOpen、@OnClose、@OnError注解所修饰的方法，在端点处理客户端的连接、断开、消息等事件时会自动调用
2. 点对点式WebSocker实现，流程为，在@OnOpen建立连接时获取到客户端id即userId，将userId对应的session存入Map，后续通过userId获取session进行消息发送，即可实现服务端对客户端通信
3. 订阅式WebSocket实现，客户端需要发送一条SocketMessage进行Topic订阅，在SocketMessage.Header中消息类型messageType="SUBSCRIBE",订阅主题topic="[指定的主题]"，在@OnMessage接受到此消息时，服务端将Topic与订阅该Topic的session集合存储起来，后续通过Topic获取session集合，向该Topic订阅者群发消息

```java
package com.xunfeng.example.websocket;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 服务端 WebSocket 端点
 * 接口路径 ws://localhost:8080/websocket/userId;
 * 注意：此处端口为SpringBoot服务端口，即server.port=8080
 */
@Component
@Slf4j
@ServerEndpoint("/websocket/{userId}")
public class WebSocket {


    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;

    /**
     * 订阅
     */
    public static final String SUBSCRIBE = "subscribe";

    /**
     * 取消订阅
     */
    public static final String UNSUBSCRIBE = "unsubscribe";

    /**
     * 订阅主题 ->会话id列表 以Topic订阅形式进行消息推送
     */
    public static ConcurrentHashMap<String, Set<Session>> topicSessionMap = new ConcurrentHashMap<>();


    /**
     * 用户ID
     */
    private String userId;

    /**
     * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
     * 虽然@Component默认是单例模式的，但springboot还是会为每个websocket连接初始化一个bean，所以可以用一个静态set保存起来。
     * 注：底下WebSocket是当前类名
     */
    private static final CopyOnWriteArraySet<WebSocket> WEB_SOCKETS = new CopyOnWriteArraySet<>();

    /**
     * 用来存在线连接用户信息
     */
    private static final ConcurrentHashMap<String, Session> SESSION_POOL = new ConcurrentHashMap<String, Session>();

    /**
     * 链接成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "userId") String userId) {
        try {
            this.session = session;
            this.userId = userId;
            WEB_SOCKETS.add(this);
            SESSION_POOL.put(userId, session);
            log.info("【websocket消息】有新的连接，总数为:" + WEB_SOCKETS.size());
            log.info("线程:{}", Thread.currentThread().getName());
            session.getAsyncRemote().sendText("已连接");
        } catch (Exception e) {
        }
    }

    /**
     * 链接关闭调用的方法
     * Topic订阅形式也需要在断开时，删除topicSessionMap中的session
     */
    @OnClose
    public void onClose() {
        try {
            WEB_SOCKETS.remove(this);
            SESSION_POOL.remove(this.userId);
            log.info("【websocket消息】连接断开，总数为:" + WEB_SOCKETS.size());
        } catch (Exception e) {
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message
     * @param session
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        if (StringUtils.isEmpty(message)) {
            session.getAsyncRemote().sendText("消息为空");
            return;
        }
        try {
            SocketResult socketResult = JSONObject.parseObject(message, SocketResult.class);
            if (null == socketResult.getHeader() || null == socketResult.getHeader().getMessageType()) {
                return;
            }
            String messageType = socketResult.getHeader().getMessageType();
            String topic = socketResult.getHeader().getTopic();
            if (SUBSCRIBE.equals(messageType)) {
                //按照Topic订阅
                subscribeTopic(topic, session);
            } else if (UNSUBSCRIBE.equals(messageType)) {
                //按照Topic取消订阅
                unsubscribeTopic(topic, session);
            }
        } catch (JSONException e) {
            log.error("json格式化错误,不为订阅式消息");
        }
        log.info("【websocket消息】收到客户端消息:" + message);
    }

    /**
     * 发送错误时的处理
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {

        log.error("用户错误,原因:" + error.getMessage());
        error.printStackTrace();
    }

    // 以下为点对点式！！

    /**
     * 向所有连接客户端广播消息
     *
     * @param message
     */
    public void sendAllMessage(String message) {
        log.info("【websocket消息】广播消息:" + message);
        for (WebSocket webSocket : WEB_SOCKETS) {
            try {
                if (webSocket.session.isOpen()) {
                    webSocket.session.getAsyncRemote().sendText(message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 对指定客户端进行单点消息发送
     */
    public void sendOneMessage(String userId, String message) {
        Session session = SESSION_POOL.get(userId);
        if (session != null && session.isOpen()) {
            try {
                log.info("【websocket消息】 单点消息:" + message);
                session.getAsyncRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 对指定客户端(多人)进行单点消息发送
     *
     * @param userIds
     * @param message
     */
    public void sendMoreMessage(String[] userIds, String message) {
        for (String userId : userIds) {
            Session session = SESSION_POOL.get(userId);
            if (session != null && session.isOpen()) {
                try {
                    log.info("【websocket消息】 单点消息:" + message);
                    session.getAsyncRemote().sendText(message);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 以下为订阅式！！

    /**
     * 订阅Topic
     *
     * @param topic
     * @param session
     */
    private void subscribeTopic(String topic, Session session) {
        Set<Session> sessions = topicSessionMap.contains(topic) ? topicSessionMap.get(topic) : new HashSet<Session>();
        sessions.add(session);
        topicSessionMap.put(topic, sessions);
        log.info("订阅Topic:{},sessionId:{}", topic, session.getId());
    }

    /**
     * 取消订阅Topic
     *
     * @param topic
     * @param session
     */
    private void unsubscribeTopic(String topic, Session session) {
        Set<Session> sessions = topicSessionMap.get(topic);
        if (CollectionUtils.isEmpty(sessions)) {
            log.info("该sessionId:{},未订阅Topic:{},无法取消", session.getId(), topic);
            return;
        }
        sessions.remove(session);
        topicSessionMap.put(topic, sessions);
        log.info("取消订阅Topic:{},sessionId:{}", topic, session.getId());
    }

    /**
     * 以Topic订阅形式进行消息推送
     *
     * @param topic
     * @param socketResult
     */
    public void sendMessageByTopic(String topic, SocketResult socketResult) {
        Set<Session> sessions = topicSessionMap.get(topic);
        sessions.stream().forEach(item -> {
            item.getAsyncRemote().sendText(JSONObject.toJSONString(socketResult));
        });
    }
}

```

### 创建WebSocket配置类

将端点（上面创建的WebSocket类）注入进Spring

```java
package com.xunfeng.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket配置类
 * 定义好端点后，需要在配置类中通过定义 ServerEndpointExporter Bean 进行注册
 */
@Configuration
public class WebSocketConfig {
    /**
     * 注入ServerEndpointExporter，
     * 这个bean会自动注册使用了@ServerEndpoint注解声明的Websocket endpoint
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}

```

### 创建SocketMessage类

此类作为订阅式中的数据载体和格式规范，可根据需求自定义开发，客户端和服务端共同遵循该格式，实现Topic订阅机制。

其中SocketMessage.header中声明消息的类型、订阅主题等各种自定义信息，而SocketMessage.body中为业务数据。

在OnMessage中接受到订阅式消息时，推荐根据消息类型messageType组装成事件，将非订阅、取消订阅类的客户端信息抛给上层业务处理。

```java
package com.xunfeng.example.websocket;

import lombok.Data;

/**
 * 请求响应体
 *
 * @author Lee
 * @return
 * @date 2022-3-29 16:46
 */
@Data
public class SocketMessage {

    /**
     * 请求头
     */
    private Header header;

    @Data
    public static class Header {
        private String version;
        private Long timestamp;
        /**
         * 消息类型
         */
        private String messageType;
        /**
         * 订阅主题
         */
        private String topic;
        private String callId;
    }

    /**
     * 请求体 body
     */
    private Object body;

    /**
     * 创建请求头
     */
    public static Header createHeader(String messageType, String topic) {
        Header header = new Header();
        header.setVersion("V1.0");
        header.setTimestamp(System.currentTimeMillis());
        header.setMessageType(messageType);
        header.setTopic(topic);
        header.setCallId(header.getTimestamp().toString());
        return header;
    }

    /**
     * 创建推送结果
     *
     * @param topic
     * @param body
     */
    public static SocketMessage createSocketResult(String messageType, String topic, Object body) {
        SocketMessage message = new SocketMessage();
        message.setHeader(createHeader(messageType, topic));
        message.setBody(body);
        return message;
    }
}

```





