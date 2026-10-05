package com.bbh.itss.dso.portal.adapter.in.web

import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders

final class WebMvc {

    private WebMvc() {
    }

    static MockMvc of(Object controller) {
        MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(), new NormalizedRequestBodies())
                .build()
    }
}
