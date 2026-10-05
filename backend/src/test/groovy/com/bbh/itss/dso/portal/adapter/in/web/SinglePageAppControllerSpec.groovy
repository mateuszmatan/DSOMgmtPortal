package com.bbh.itss.dso.portal.adapter.in.web

import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class SinglePageAppControllerSpec extends Specification {

    MockMvc mvc = MockMvcBuilders.standaloneSetup(new SinglePageAppController()).build()

    def "the app's page #path is served by the Angular index page"() {
        when:
        def response = mvc.perform(get(path)).andReturn().response

        then:
        response.forwardedUrl == '/index.html'

        where:
        path << ['/products', '/products/new', '/products/12/edit', '/monitoring', '/monitoring/pipelines/7']
    }

    def "other paths are left to the API and the static resources"() {
        expect:
        mvc.perform(get(path)).andReturn().response.status == 404

        where:
        path << ['/api/unknown', '/favicon.ico']
    }
}
