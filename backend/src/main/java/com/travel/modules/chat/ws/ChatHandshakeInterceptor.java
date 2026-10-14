package com.travel.modules.chat.ws;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * WS 握手鉴权：浏览器无法自定义请求头，故从查询参数 ?token= 取 Sa-Token 校验。
 * 校验通过后把 userId / sessionId 写入握手属性，供 handler 使用。
 */
@Slf4j
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_SESSION_ID = "sessionId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (!StringUtils.hasText(token)) {
            log.debug("WS 握手缺少 token");
            return false;
        }
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            log.debug("WS 握手 token 无效");
            return false;
        }
        attributes.put(ATTR_USER_ID, Long.valueOf(loginId.toString()));
        attributes.put(ATTR_SESSION_ID, extractSessionId(request.getURI().getPath()));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private Long extractSessionId(String path) {
        int idx = path.lastIndexOf('/');
        try {
            return Long.valueOf(path.substring(idx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
