package com.system.ratelimiter.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping("/")
    public String forwardToIndex() {
        return "forward:/index.html";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "forward:/index.html";
    }

    @GetMapping("/analytics")
    public String analytics() {
        return "forward:/index.html";
    }

    @GetMapping("/settings")
    public String settings() {
        return "forward:/index.html";
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/index.html";
    }
}
