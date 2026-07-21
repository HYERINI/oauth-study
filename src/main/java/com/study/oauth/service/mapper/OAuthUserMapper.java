package com.study.oauth.service.mapper;

import com.study.oauth.dto.OAuthUser;

import java.util.Map;

/**
 * 제공자별 사용자 정보 JSON 을 공통 {@link OAuthUser} 로 변환하는 전략(strategy).
 *
 * <p>[OCP] 제공자마다 응답 JSON 모양이 다르다. 예전엔 OAuthClient 안에서 switch(provider) 로
 *    갈랐는데, 그러면 새 제공자(예: naver)를 추가할 때마다 기존 코드를 열어 case 를 넣어야 했다(변경에 열림).
 *    이 인터페이스를 두면 "새 제공자 = 이 인터페이스를 구현한 새 클래스 하나 추가"로 끝난다.
 *    기존 코드는 건드리지 않는다 → 확장에는 열려있고 변경에는 닫혀있다.
 */
public interface OAuthUserMapper {

    /** 이 매퍼가 담당하는 provider 이름 (application.yml 의 키와 일치: google/kakao/github) */
    String provider();

    /** 제공자 응답 본문(JSON→Map)을 공통 OAuthUser 로 변환 */
    OAuthUser map(Map<String, Object> body);
}
