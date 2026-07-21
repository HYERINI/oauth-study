package com.study.oauth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 첫 화면. 로그인 버튼 3개(구글/카카오/깃허브)를 보여준다.
 * 각 버튼은 우리 서버의 /oauth/{provider}/login 으로 연결된다 → OAuthController.login() 실행.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home"; // templates/home.html 렌더링
    }
}
