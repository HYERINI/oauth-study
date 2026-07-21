package com.study.oauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * application.yml 의 oauth.providers.* 를 자바 객체로 그대로 옮겨 담는 클래스.
 *
 * <p>prefix = "oauth" 이므로 yml 의 `oauth:` 아래 트리가 이 객체로 바인딩된다.
 * <p>providers 는 "google/kakao/github" → 각 설정(Provider) 의 Map 이 된다.
 *    덕분에 컨트롤러에서 providers.get("google") 처럼 provider 이름만으로 설정을 꺼내
 *    3개 제공자를 "똑같은 코드 한 벌"로 처리할 수 있다.
 */
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    /** key = provider 이름(google/kakao/github), value = 해당 제공자 설정 */
    private Map<String, Provider> providers;

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers;
    }

    /** 편의 메서드: 없는 provider 이름이 들어오면 명확한 에러를 던진다. */
    public Provider get(String providerName) {
        Provider provider = providers == null ? null : providers.get(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("지원하지 않는 provider: " + providerName);
        }
        return provider;
    }

    /**
     * 제공자 하나의 설정 묶음. (yml 의 client-id → 자바의 clientId 로 자동 변환되어 바인딩)
     */
    public static class Provider {
        private String clientId;         // 인화앱이 발급받은 공개 식별자
        private String clientSecret;     // 서버만 아는 비밀 (뒷길에서만 사용)
        private String authorizationUri; // 사용자를 보내 로그인/동의를 받는 곳
        private String tokenUri;         // code → access_token 교환하는 곳
        private String userInfoUri;      // access_token 으로 유저 정보를 얻는 곳
        private String redirectUri;      // 끝나면 사용자를 되돌려보낼 우리 콜백 주소
        private String scope;            // 요청할 권한 범위

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

        public String getAuthorizationUri() { return authorizationUri; }
        public void setAuthorizationUri(String authorizationUri) { this.authorizationUri = authorizationUri; }

        public String getTokenUri() { return tokenUri; }
        public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }

        public String getUserInfoUri() { return userInfoUri; }
        public void setUserInfoUri(String userInfoUri) { this.userInfoUri = userInfoUri; }

        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
    }
}
