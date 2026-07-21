package com.study.oauth.controller;

import com.study.oauth.config.OAuthProperties;
import com.study.oauth.dto.OAuthUser;
import com.study.oauth.service.OAuthClient;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * OAuth 로그인의 "앞길(브라우저 경유)"을 담당하는 컨트롤러.
 *
 * <p>우리가 대화에서 그린 흐름이 여기 두 개의 메서드로 나뉜다:
 *   - GET /oauth/{provider}/login    → (1) state 만들고 세션에 저장 후, 제공자로 302 리다이렉트
 *   - GET /oauth/{provider}/callback → (4) state 대조 → (6) code를 토큰으로 교환 → (7) 유저 정보
 *
 * <p>{provider} 자리에 google / kakao / github 가 들어가고, 설정만 바꿔 끼워
 *    "코드 한 벌"로 세 제공자를 모두 처리한다.
 */
@Controller
public class OAuthController {

    private final OAuthProperties properties;
    private final OAuthClient oAuthClient;

    // 세션에 state 를 저장할 때 쓸 key 이름. (provider별로 구분해서 저장한다)
    private static final String SESSION_STATE_KEY_PREFIX = "oauth_state_";

    // [PKCE] 세션에 code_verifier(열쇠 원본)를 저장할 때 쓸 key 이름.
    private static final String SESSION_VERIFIER_KEY_PREFIX = "oauth_verifier_";

    // 예측 불가능한 랜덤값을 만드는 보안용 난수 생성기.
    // 일반 Random 이 아니라 SecureRandom 을 쓰는 이유: state 는 "추측 불가능"해야 의미가 있기 때문.
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthController(OAuthProperties properties, OAuthClient oAuthClient) {
        this.properties = properties;
        this.oAuthClient = oAuthClient;
    }

    // ─────────────────────────────────────────────────────────────
    //  (1) 로그인 시작 : state 생성 → 세션 저장 → 제공자로 리다이렉트
    // ─────────────────────────────────────────────────────────────
    /**
     * 사용자가 "구글로 로그인" 버튼을 누르면 이 주소로 온다. (예: /oauth/google/login)
     *
     * <p>여기서 하는 일은 딱 대화에서 정리한 (1)번:
     *   ① 예측 불가능한 랜덤 state 생성
     *   ② 그 state 를 "서버 세션"에 저장  (브라우저에는 세션ID(번호표)만 쿠키로 나감)
     *   ③ state 를 붙인 제공자 로그인 URL을 만들어 302 리다이렉트
     *
     * <p>★ 응답은 JSON이 아니라 "302 리다이렉트"다. 브라우저는 값을 받는 게 아니라
     *    시키는 주소(제공자 로그인 페이지)로 그냥 이동한다. state 사본 하나는 그 URL을 타고
     *    제공자까지 갔다 오고, 다른 사본 하나는 서버 세션에 조용히 남는다.
     */
    @GetMapping("/oauth/{provider}/login")
    public void login(@PathVariable String provider,
                      HttpSession session,
                      HttpServletResponse response) throws IOException {

        OAuthProperties.Provider config = properties.get(provider);

        // ① 랜덤 state 생성 (32바이트 → URL 안전 Base64 문자열)
        String state = generateState();

        // ② 서버 세션에 저장. ★ 이 값이 나중에 콜백에서 "대조 기준"이 된다.
        //    실제 값은 서버가 들고 있고, 브라우저에는 이 세션을 여는 번호표(JSESSIONID 쿠키)만 나간다.
        session.setAttribute(SESSION_STATE_KEY_PREFIX + provider, state);

        // ①-2 [PKCE] 열쇠(code_verifier) 생성 → 세션에 보관, 자물쇠(code_challenge)만 URL로 보낸다.
        //     - verifier(열쇠)  : 절대 네트워크로 안 나감. 세션에만 둔다. 토큰 교환 때만 꺼내 쓴다.
        //     - challenge(자물쇠): verifier 를 SHA-256 해시한 값. 이것만 로그인 URL에 실어 보낸다.
        //     해시는 단방향이라, 중간에서 challenge 를 훔쳐봐도 verifier 를 되돌릴 수 없다.
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = createCodeChallenge(codeVerifier);
        session.setAttribute(SESSION_VERIFIER_KEY_PREFIX + provider, codeVerifier);

        // ③ 제공자 로그인 URL 조립. (?response_type=code&client_id=...&redirect_uri=...&scope=...&state=...)
        String authorizationUrl = UriComponentsBuilder
                .fromUriString(config.getAuthorizationUri())
                .queryParam("response_type", "code")            // "코드 방식(Authorization Code)으로 줘"
                .queryParam("client_id", config.getClientId())
                .queryParam("redirect_uri", config.getRedirectUri()) // 끝나면 우리 콜백으로 돌려보내
                .queryParam("scope", config.getScope())         // 요청 권한 범위
                .queryParam("state", state)                     // ★ 방금 만든 state 를 URL에 실어 보냄
                .queryParam("code_challenge", codeChallenge)    // ★ [PKCE] 자물쇠만 보냄 (열쇠는 세션에 숨김)
                .queryParam("code_challenge_method", "S256")    // ★ [PKCE] "SHA-256으로 해시했음" 을 알림
                .build()
                .toUriString();

        // 302 리다이렉트 — 사용자를 제공자의 로그인/동의 화면으로 이동시킨다.
        // 여기서부터 (2)단계(제공자 화면에서 로그인+동의)는 우리 코드가 관여하지 않는다.
        response.sendRedirect(authorizationUrl);
    }

    // ─────────────────────────────────────────────────────────────
    //  (4~7) 콜백 : state 대조 → code를 토큰으로 교환 → 유저 정보 조회
    // ─────────────────────────────────────────────────────────────
    /**
     * (3)에서 제공자가 사용자를 여기로 되돌려보낸다.
     *    URL 예: /oauth/google/callback?code=4/0Ae...&state=xY3k...
     *
     * <p>제공자는 (1)에서 받은 state 를 토씨 하나 안 바꾸고 "메아리처럼" 그대로 돌려준다.
     *    우리는 code로 토큰 교환을 하기 "전에" 먼저 state 부터 검사한다.
     */
    @GetMapping("/oauth/{provider}/callback")
    public String callback(@PathVariable String provider,
                           @RequestParam String code,   // URL로 돌아온 임시 코드
                           @RequestParam String state,  // URL로 돌아온 state (제공자가 되돌려준 값)
                           HttpSession session,
                           Model model) {

        OAuthProperties.Provider config = properties.get(provider);

        // ── (4) state 대조 ── (우리가 오래 얘기한 CSRF 방어의 실체)
        // 세션에 저장해둔 state(진짜 값)를 번호표(세션 쿠키)로 찾아 꺼낸다.
        String savedState = (String) session.getAttribute(SESSION_STATE_KEY_PREFIX + provider);

        // [PKCE] 로그인 시작 때 세션에 넣어둔 열쇠(code_verifier)를 꺼낸다.
        String codeVerifier = (String) session.getAttribute(SESSION_VERIFIER_KEY_PREFIX + provider);

        // 한 번 쓴 state / verifier 는 재사용 못 하게 즉시 제거(재생 공격 방지).
        session.removeAttribute(SESSION_STATE_KEY_PREFIX + provider);
        session.removeAttribute(SESSION_VERIFIER_KEY_PREFIX + provider);

        // "URL로 온 state" == "세션에 저장해둔 state" 여야만 통과.
        //  - 쿠키를 지웠거나 다른 브라우저면 savedState 가 null → 불일치 → 거절
        //  - 공격자가 콜백 URL만 복붙해 남에게 던져도, 그 브라우저 세션엔 이 state가 없어 거절
        // 즉 "URL만으로는 아무것도 못 한다" = 이 요청이 진짜 내가 시작한 로그인의 결과임을 증명.
        if (savedState == null || !savedState.equals(state)) {
            model.addAttribute("error",
                    "state 불일치! 위조되었거나 세션이 없는 요청이라 폐기했습니다. "
                    + "(세션 state=" + savedState + ", URL state=" + state + ")");
            return "result"; // 토큰 교환으로 넘어가지 않고 여기서 끝낸다.
        }

        // 여기 도달 = state 검증 통과. 이제 안심하고 뒷길 통신을 진행한다.

        // ── (6) code → access_token 교환 (뒷길: 서버 ↔ 제공자, 브라우저 안 거침) ──
        //    [PKCE] 여기서 열쇠(code_verifier)를 함께 보낸다. 제공자가 SHA256(verifier)를
        //    로그인 때 받아둔 challenge 와 대조해, 맞아야만 토큰을 내준다.
        String accessToken = oAuthClient.exchangeCodeForToken(config, code, codeVerifier);

        // ── (7) access_token(카드키)으로 사용자 정보 조회 ──
        OAuthUser user = oAuthClient.fetchUserInfo(provider, config, accessToken);

        // 결과를 화면에 표시 (학습용). 실제 서비스라면 여기서 우리 서비스의 회원가입/로그인 처리를 한다.
        model.addAttribute("user", user);
        // 토큰은 원래 화면에 노출하면 안 되지만, 학습 목적이라 "앞 일부만" 잘라 보여준다.
        model.addAttribute("tokenPreview", accessToken.substring(0, Math.min(12, accessToken.length())) + "...");
        return "result";
    }

    /**
     * 예측 불가능한 랜덤 state 문자열 생성.
     * 32바이트 난수 → URL에 그대로 실어도 안전한 Base64(URL-safe, 패딩 없음) 문자열로 변환.
     */
    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * [PKCE] 열쇠(code_verifier) 생성.
     * 32바이트 난수 → URL 안전 Base64(43자). 매 로그인마다 새로 만드는 "일회용 비밀번호".
     */
    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * [PKCE] 자물쇠(code_challenge) 생성 = BASE64URL( SHA-256( code_verifier ) ).
     *
     * <p>열쇠를 SHA-256으로 해시한 값. 해시는 단방향이라 이 값(자물쇠)에서 원래 열쇠를 되돌릴 수 없다.
     *    그래서 이 값은 로그인 URL에 노출돼도 안전하다. (진짜 방패는 세션에만 있는 열쇠)
     */
    private String createCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 자바 표준에 있어 실제로는 발생하지 않는다.
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }
}
