package com.bbh.itss.dso.portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Angular app for the paths of its pages, so that reloading a page or opening a bookmark works.
 * The app itself then shows the page the path names.
 */
@Controller
public class SinglePageAppController {

    @GetMapping({"/products", "/products/**", "/monitoring", "/monitoring/**"})
    public String app() {
        return "forward:/index.html";
    }
}
