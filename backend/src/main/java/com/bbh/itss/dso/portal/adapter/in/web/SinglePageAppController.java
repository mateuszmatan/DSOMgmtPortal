package com.bbh.itss.dso.portal.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SinglePageAppController {

    @GetMapping({"/self-service", "/self-service/**", "/monitoring", "/monitoring/**", "/evidence", "/evidence/**",
            "/admin", "/admin/**", "/beadle", "/beadle/**", "/products", "/products/**", "/settings", "/settings/**"})
    public String app() {
        return "forward:/index.html";
    }
}
