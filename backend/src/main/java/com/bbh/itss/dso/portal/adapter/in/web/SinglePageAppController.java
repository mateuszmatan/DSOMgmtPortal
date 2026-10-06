package com.bbh.itss.dso.portal.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SinglePageAppController {

    @GetMapping({"/products", "/products/**", "/monitoring", "/monitoring/**", "/evidence", "/evidence/**", "/settings",
            "/settings/**", "/beadle", "/beadle/**"})
    public String app() {
        return "forward:/index.html";
    }
}
