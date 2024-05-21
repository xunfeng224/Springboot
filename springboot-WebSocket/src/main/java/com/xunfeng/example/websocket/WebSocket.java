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
            SocketMessage socketMessage = JSONObject.parseObject(message, SocketMessage.class);
            if (null == socketMessage.getHeader() || null == socketMessage.getHeader().getMessageType()) {
                return;
            }
            String messageType = socketMessage.getHeader().getMessageType();
            String topic = socketMessage.getHeader().getTopic();
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
     * @param message
     */
    public void sendMessageByTopic(String topic, SocketMessage message) {
        Set<Session> sessions = topicSessionMap.get(topic);
        if (CollectionUtils.isEmpty(sessions)) {
            log.info("该Topic:{},不存在订阅者,无法发送", topic);
            return;
        }
        sessions.forEach(item -> {
            item.getAsyncRemote().sendText(JSONObject.toJSONString(message));
        });
    }
}
