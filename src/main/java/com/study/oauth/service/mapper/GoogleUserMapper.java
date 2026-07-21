package com.study.oauth.service.mapper;

import com.study.oauth.dto.OAuthUser;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 구글 userinfo 응답 매퍼.
 * 응답 예: { "id": "...", "email": "...", "name": "...", "picture": "..." }
 */
@Component
public class GoogleUserMapper implements OAuthUserMapper {

    @Override
    public String provider() {
        return "google";
    }

    @Override
    public OAuthUser map(Map<String, Object> body) {
        return new OAuthUser(
                "google",
                String.valueOf(body.get("id")),
                String.valueOf(body.get("name")),
                (String) body.get("email")
        );
    }
}
