package com.study.oauth.service.mapper;

import com.study.oauth.dto.OAuthUser;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 카카오 user 응답 매퍼. 정보가 중첩 구조라 파고들어 꺼낸다.
 * 응답 예:
 * {
 *   "id": 123456789,
 *   "properties": { "nickname": "홍길동" },
 *   "kakao_account": { "email": "hong@kakao.com", "profile": { "nickname": "홍길동" } }
 * }
 */
@Component
public class KakaoUserMapper implements OAuthUserMapper {

    @Override
    public String provider() {
        return "kakao";
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUser map(Map<String, Object> body) {
        String id = String.valueOf(body.get("id"));

        String nickname = null;
        String email = null;

        Map<String, Object> properties = (Map<String, Object>) body.get("properties");
        if (properties != null) {
            nickname = (String) properties.get("nickname");
        }

        Map<String, Object> account = (Map<String, Object>) body.get("kakao_account");
        if (account != null) {
            email = (String) account.get("email"); // 이메일 동의항목을 안 켰으면 null
            if (nickname == null) {
                Map<String, Object> profile = (Map<String, Object>) account.get("profile");
                if (profile != null) {
                    nickname = (String) profile.get("nickname");
                }
            }
        }
        return new OAuthUser("kakao", id, nickname, email);
    }
}
