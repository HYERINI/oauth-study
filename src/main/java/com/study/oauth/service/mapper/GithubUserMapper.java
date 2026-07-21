package com.study.oauth.service.mapper;

import com.study.oauth.dto.OAuthUser;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 깃허브 user 응답 매퍼.
 * 응답 예: { "id": 123, "login": "octocat", "name": "The Octocat", "email": null }
 */
@Component
public class GithubUserMapper implements OAuthUserMapper {

    @Override
    public String provider() {
        return "github";
    }

    @Override
    public OAuthUser map(Map<String, Object> body) {
        // 실명(name)이 비공개면 null → 로그인 아이디(login)로 대체
        String name = body.get("name") != null
                ? String.valueOf(body.get("name"))
                : String.valueOf(body.get("login"));
        return new OAuthUser(
                "github",
                String.valueOf(body.get("id")),
                name,
                (String) body.get("email") // 이메일 비공개면 null (별도 /user/emails 호출 필요)
        );
    }
}
