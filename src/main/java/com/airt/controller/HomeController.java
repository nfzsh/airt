package com.airt.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页控制器
 * 用于提供前端页面访问
 */
@Controller
public class HomeController {

    /**
     * 首页 - 返回前端 HTML 页面
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    /**
     * 健康检查页面
     */
    @GetMapping("/health")
    public String health() {
        return "forward:/index.html";
    }
}
