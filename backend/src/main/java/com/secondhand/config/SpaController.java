package com.secondhand.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由转发。
 * 前端 Vue Router 使用 history 模式，刷新页面时需要将非 API 路径转发到 index.html。
 */
@Controller
public class SpaController {

    @GetMapping({"/login", "/sell", "/products/**", "/orders/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
