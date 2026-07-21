package com.study.oauth.dto;

/**
 * 제공자마다 유저 정보 JSON 모양이 다 다르다(구글/카카오/깃허브).
 * 그걸 우리 앱이 쓰기 좋은 "공통 형태"로 정규화해 담는 그릇.
 *
 * @param provider 어느 제공자로 로그인했는지 (google/kakao/github)
 * @param id       그 제공자 안에서의 고유 사용자 id
 * @param name     표시용 이름/닉네임
 * @param email    이메일(제공자·동의범위에 따라 없을 수 있어 null 가능)
 */
public record OAuthUser(
        String provider,
        String id,
        String name,
        String email
) {
}
