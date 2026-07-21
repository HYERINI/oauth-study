package com.study.oauth;

import com.study.oauth.config.OAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 애플리케이션 시작점.
 *
 * <p>@SpringBootApplication : 스프링 부트 앱임을 선언(컴포넌트 스캔 + 자동설정).
 * <p>@EnableConfigurationProperties(OAuthProperties.class)
 *    : application.yml 의 oauth.* 설정을 OAuthProperties 객체로 바인딩해서 쓸 수 있게 등록.
 */
@SpringBootApplication
@EnableConfigurationProperties(OAuthProperties.class)
public class OauthStudyApplication {

    public static void main(String[] args) {
        SpringApplication.run(OauthStudyApplication.class, args);
        // 뜨고 나면 브라우저에서 http://localhost:8080 접속 → 로그인 버튼 3개가 보인다.
    }
}
