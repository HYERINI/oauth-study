# OAuth 2.0 로그인 데모 (Spring Boot)

Google / Kakao / GitHub 소셜 로그인을, **Spring Security의 자동 마법 없이 직접** 구현한 학습용 프로젝트다.
목적은 OAuth의 흐름 — `state 생성 → 세션 저장 → 리다이렉트 → 콜백 state 대조 → code를 토큰으로 교환 → 토큰으로 유저정보` — 을 **코드에서 눈으로 보는 것**.

## 전체 흐름 (인화앱 비유 그대로)

```
[브라우저]         [우리 서버]                 [제공자 Auth 서버]        [제공자 API]
   |                  |                            |                       |
 (0) 로그인 클릭 ───> |                            |                       |
   |   (1) state 생성 → 세션 저장 → 302 리다이렉트                        |
   | <──────────────  |                            |                       |
 (2) 제공자서 로그인+동의 ─────────────────────> |                       |
   |   (3) ?code=..&state=.. 달고 콜백으로 되돌려보냄                     |
   | <──────────────────────────────────────────  |                       |
 (4) /callback ─────> |                            |                       |
   |   (5) state 대조 → (6) code+secret로 토큰 교환(뒷길)                 |
   |                  | ─────────────────────────> |                       |
   |                  | <──── access_token ─────── |                       |
   |   (7) 토큰으로 유저정보 요청                                          |
   |                  | ───────────────────────────────────────────────> |
   |                  | <──────────── 유저정보 ─────────────────────────  |
   | <── 결과 화면 ── |                            |                       |
```

핵심 코드 위치:
- **(1) 로그인 시작** — `OAuthController.login()` : state 생성 + 세션 저장 + 리다이렉트
- **(4~5) state 대조** — `OAuthController.callback()` 앞부분
- **(6~7) 토큰 교환·유저정보** — `OAuthClient`

## 실행 방법

### 1. 각 제공자에 앱 등록하고 자격증명(client id/secret) 발급받기

> 콜백(Redirect URI)은 반드시 아래 값과 **정확히 일치**하게 등록해야 한다.

| 제공자 | 콘솔 | 등록할 Redirect URI |
|--------|------|---------------------|
| Google | [console.cloud.google.com](https://console.cloud.google.com) → API 및 서비스 → 사용자 인증 정보 → OAuth 클라이언트 ID | `http://localhost:8080/oauth/google/callback` |
| Kakao  | [developers.kakao.com](https://developers.kakao.com) → 내 애플리케이션 → 카카오 로그인 → Redirect URI | `http://localhost:8080/oauth/kakao/callback` |
| GitHub | [github.com/settings/developers](https://github.com/settings/developers) → New OAuth App | `http://localhost:8080/oauth/github/callback` |

- Kakao: "카카오 로그인" 활성화 필요. `client-id`는 **REST API 키**를 쓴다. (JavaScript 키 아님)
- GitHub: 별도 활성화 없이 OAuth App 만들면 바로 Client ID/Secret이 나온다.

### 2. 자격증명을 환경변수로 주입

소스에 직접 넣지 말고 환경변수로 전달한다 (`application.yml`이 이 값을 읽는다).

```bash
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export KAKAO_CLIENT_ID=...          # 카카오 REST API 키
export KAKAO_CLIENT_SECRET=...      # 콘솔에서 client_secret을 켰을 때만. 안 켰으면 생략 가능
export GITHUB_CLIENT_ID=...
export GITHUB_CLIENT_SECRET=...
```

### 3. 실행

```bash
./gradlew bootRun
```

브라우저에서 http://localhost:8080 접속 → 원하는 제공자 버튼 클릭 → 로그인/동의 → 결과 화면.

## 직접 실험해보면 좋은 것 (대화에서 나온 것들)

1. **콜백 URL만 복붙해서 새 브라우저(또는 시크릿창)에서 열기**
   → 세션 쿠키(번호표)가 없어 `state 불일치`로 거절되는 걸 확인. (`state`의 CSRF 방어)
2. 로그인 도중 개발자도구에서 **쿠키(JSESSIONID) 삭제** 후 콜백 진행 → 역시 거절.
3. `application.yml`의 `scope`를 바꿔보고 동의 화면의 요청 권한이 달라지는지 확인.

## 다음 단계

지금은 `state`까지만 구현돼 있다. 다음 학습으로 **PKCE**(`code_verifier`/`code_challenge`)를 이 흐름 위에 얹으면,
`client_secret`을 숨길 수 없는 SPA/모바일 환경에서 code 탈취를 막는 방법까지 이어진다.
