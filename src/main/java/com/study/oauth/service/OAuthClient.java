package com.study.oauth.service;

import com.study.oauth.config.OAuthProperties;
import com.study.oauth.dto.OAuthUser;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * OAuth의 "뒷길(서버 ↔ 제공자 서버)" 통신을 담당하는 서비스.
 *
 * <p>대화에서 정리한 두 단계가 여기에 그대로 들어있다:
 *   1) exchangeCodeForToken : 임시 code + client_secret  →  access_token  (뒷길, 브라우저 안 거침)
 *   2) fetchUserInfo        : access_token(카드키)         →  실제 사용자 정보
 *
 * <p>이 두 통신은 브라우저를 거치지 않고 우리 서버가 제공자 서버에 직접 요청한다.
 *    그래서 여기서 client_secret 같은 진짜 비밀을 붙여도 안전하다.
 */
@Service
public class OAuthClient {

    // 스프링이 제공하는 HTTP 클라이언트. 우리 서버가 남의 서버(구글 등)를 호출할 때 쓴다.
    private final RestClient restClient = RestClient.create();

    /**
     * (6단계) 임시 code 를 access_token 으로 교환한다.
     *
     * <p>왜 굳이 교환하나? code 는 콜백에서 브라우저 URL에 노출됐던 값이라 그 자체론 위험하다.
     *    그래서 code 하나만으론 아무것도 못 하게 만들고, 여기서 client_secret(서버만 아는 비밀)을
     *    함께 제시해야만 진짜 토큰을 내주도록 설계돼 있다. = "앞길엔 code만, 뒷길에서 토큰 교환".
     */
    public String exchangeCodeForToken(OAuthProperties.Provider provider, String code) {
        // application/x-www-form-urlencoded 형식의 폼 바디를 만든다. (OAuth 토큰 엔드포인트 표준 형식)
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");      // "인가 코드 방식으로 토큰 달라"
        form.add("code", code);                            // 방금 콜백으로 받은 임시 코드
        form.add("client_id", provider.getClientId());
        form.add("client_secret", provider.getClientSecret()); // ★ 서버만 아는 비밀 (뒷길이라 붙여도 안전)
        form.add("redirect_uri", provider.getRedirectUri());   // 처음 요청 때와 동일해야 함(제공자가 대조)

        // 제공자 서버로 POST. 응답 JSON을 Map으로 받는다.
        // Accept: application/json  →  특히 깃허브는 이 헤더가 없으면 JSON이 아니라
        //         "access_token=xxx&scope=..." 같은 폼 문자열로 응답한다. 그래서 명시적으로 요청.
        Map<String, Object> response = restClient.post()
                .uri(provider.getTokenUri())
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("토큰 교환 실패. 응답: " + response);
        }
        // 응답 예시(구글):
        // { "access_token": "ya29...", "expires_in": 3599, "refresh_token": "1//..", "scope": "...", "token_type": "Bearer" }
        return response.get("access_token").toString();
    }

    /**
     * (7단계) access_token(카드키)으로 Resource Server(검문소)에서 사용자 정보를 가져온다.
     *
     * <p>토큰은 "Authorization: Bearer {토큰}" 헤더에 실어 제시한다.
     *    제공자는 이 토큰이 유효한지 + scope에 이 데이터 접근 권한이 있는지 확인 후 데이터를 준다.
     *
     * <p>제공자마다 응답 JSON 모양이 달라서, provider 이름으로 갈라 파싱한 뒤 공통 OAuthUser로 정규화한다.
     */
    public OAuthUser fetchUserInfo(String providerName, OAuthProperties.Provider provider, String accessToken) {
        Map<String, Object> body = restClient.get()
                .uri(provider.getUserInfoUri())
                .header("Authorization", "Bearer " + accessToken) // ★ "나 이 토큰 가진 사람이야"
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(Map.class);

        if (body == null) {
            throw new IllegalStateException("사용자 정보 응답이 비어있음");
        }

        // ── 제공자별 JSON 모양이 다르므로 각각 맞춰서 꺼낸다 ──
        return switch (providerName) {
            case "google" -> new OAuthUser(
                    "google",
                    String.valueOf(body.get("id")),
                    String.valueOf(body.get("name")),
                    (String) body.get("email")
            );
            case "github" -> new OAuthUser(
                    "github",
                    String.valueOf(body.get("id")),
                    // 깃허브는 실명(name)이 비공개면 null → 로그인 아이디(login)로 대체
                    body.get("name") != null ? String.valueOf(body.get("name")) : String.valueOf(body.get("login")),
                    (String) body.get("email") // 이메일 비공개면 null (별도 /user/emails 호출 필요)
            );
            case "kakao" -> parseKakao(body);
            default -> throw new IllegalArgumentException("알 수 없는 provider: " + providerName);
        };
    }

    /**
     * 카카오는 정보가 중첩 구조라 별도 파싱.
     * 응답 예:
     * {
     *   "id": 123456789,
     *   "properties": { "nickname": "홍길동" },
     *   "kakao_account": { "email": "hong@kakao.com", "profile": { "nickname": "홍길동" } }
     * }
     */
    @SuppressWarnings("unchecked")
    private OAuthUser parseKakao(Map<String, Object> body) {
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
