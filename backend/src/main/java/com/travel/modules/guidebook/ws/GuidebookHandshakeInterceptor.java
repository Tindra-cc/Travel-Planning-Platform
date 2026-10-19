package com.travel.modules.guidebook.ws;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 攻略进度 WS 握手：?token= 鉴权，并校验攻略归属。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuidebookHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_GUIDEBOOK_ID = "guidebookId";

    private final GuidebookMapper guidebookMapper;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (!StringUtils.hasText(token)) {
            return false;
        }
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            return false;
        }
        Long userId = Long.valueOf(loginId.toString());
        Long guidebookId = extractId(request.getURI().getPath());
        if (guidebookId == null) {
            return false;
        }
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null || !userId.equals(guidebook.getUserId())) {
            log.debug("WS 握手拒绝：攻略不存在或非本人 userId={} guidebookId={}", userId, guidebookId);
            return false;
        }
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_GUIDEBOOK_ID, guidebookId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private Long extractId(String path) {
        int idx = path.lastIndexOf('/');
        try {
            return Long.valueOf(path.substring(idx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
