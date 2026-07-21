package com.study.oauth.service;

import com.study.oauth.config.OAuthProperties;
import com.study.oauth.dto.OAuthUser;
import com.study.oauth.service.mapper.OAuthUserMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OAuth의 "뒷길(서버 ↔ 제공자 서버)" 통신을 담당하는 서비스.
 *
 * <p>대화에서 정리한 두 단계가 여기에 그대로 들어있다:
 *   1) exchangeCodeForToken : 임시 code + client_secret  →  access_token  (뒷길, 브라우저 안 거침)
 *   2) fetchUserInfo        : access_token(카드키)         →  실제 사용자 정보
 *
 * <p>이 두 통신은 브라우저를 거치지 않고 우리 서버가 제공자 서버에 직접 요청한다.
 *    그래서 여기서 client_secret 같은 진짜 비밀을 붙여도 안전하다.
 *
 * <p>[SRP/OCP] 제공자별 응답 파싱은 이 클래스가 직접 하지 않고 {@link OAuthUserMapper} 구현체에 위임한다.
 *    이 서비스는 "HTTP 통신"에만 집중하고, "응답 정규화"는 매퍼가 담당한다.
 */
@Service
public class OAuthClient {

    // 스프링이 제공하는 HTTP 클라이언트. 우리 서버가 남의 서버(구글 등)를 호출할 때 쓴다.
    private final RestClient restClient = RestClient.create();

    // provider 이름 → 그 제공자 응답 매퍼. 스프링이 모든 OAuthUserMapper 구현체를 List로 주입해주면,
    // 여기서 provider() 를 키로 하는 Map 으로 정리해둔다. (새 제공자 추가 시 이 코드는 안 바뀐다)
    private final Map<String, OAuthUserMapper> userMappers;

    public OAuthClient(List<OAuthUserMapper> mappers) {
        this.userMappers = mappers.stream()
                .collect(Collectors.toMap(OAuthUserMapper::provider, Function.identity()));
    }

    /**
     * (6단계) 임시 code 를 access_token 으로 교환한다.
     *
     * <p>왜 굳이 교환하나? code 는 콜백에서 브라우저 URL에 노출됐던 값이라 그 자체론 위험하다.
     *    그래서 code 하나만으론 아무것도 못 하게 만들고, 여기서 client_secret(서버만 아는 비밀)을
     *    함께 제시해야만 진짜 토큰을 내주도록 설계돼 있다. = "앞길엔 code만, 뒷길에서 토큰 교환".
     *
     * <p>[PKCE] 여기에 code_verifier(열쇠 원본)를 함께 보낸다.
     *    제공자는 SHA256(code_verifier) 가 로그인 시작 때 받아둔 code_challenge(자물쇠)와 같은지 확인한다.
     *    code 를 훔쳐도 이 열쇠가 없으면 토큰 교환이 거부된다. (client_secret 없는 앱을 위한 방패)
     *
     * @param codeVerifier 로그인 시작 때 만들어 세션에 보관해둔 PKCE 열쇠 원본
     */
    public String exchangeCodeForToken(OAuthProperties.Provider provider, String code, String codeVerifier) {
        // application/x-www-form-urlencoded 형식의 폼 바디를 만든다. (OAuth 토큰 엔드포인트 표준 형식)
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");      // "인가 코드 방식으로 토큰 달라"
        form.add("code", code);                            // 방금 콜백으로 받은 임시 코드
        form.add("client_id", provider.getClientId());
        form.add("client_secret", provider.getClientSecret()); // ★ 서버만 아는 비밀 (뒷길이라 붙여도 안전)
        form.add("redirect_uri", provider.getRedirectUri());   // 처음 요청 때와 동일해야 함(제공자가 대조)
        form.add("code_verifier", codeVerifier);               // ★ [PKCE] 열쇠 원본 제시 (자물쇠와 대조됨)

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
     * <p>제공자마다 응답 JSON 모양이 다르지만, 파싱은 provider 별 {@link OAuthUserMapper} 에 위임한다.
     *    이 메서드는 "토큰으로 응답을 받아오는 것"까지만 책임진다.
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

        // provider 에 맞는 매퍼를 찾아 위임한다. (switch 제거 → 새 제공자는 매퍼 클래스만 추가하면 됨)
        OAuthUserMapper mapper = userMappers.get(providerName);
        if (mapper == null) {
            throw new IllegalArgumentException("등록된 매퍼가 없는 provider: " + providerName);
        }
        return mapper.map(body);
    }
}
