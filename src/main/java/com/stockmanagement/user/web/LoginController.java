package com.stockmanagement.user.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 카카오 로그인 화면(GET /login). 안내 문구는 쿼리 파라미터(error, logout, withdrawn)로 정한다. */
@Controller
class LoginController {

    @GetMapping("/login")
    String login() {
        return "auth/login";
    }
}
