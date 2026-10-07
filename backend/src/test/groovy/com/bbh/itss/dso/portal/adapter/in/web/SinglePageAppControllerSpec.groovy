package com.bbh.itss.dso.portal.adapter.in.web

import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class SinglePageAppControllerSpec extends Specification {

    MockMvc mvc = standaloneSetup(new SinglePageAppController()).build()

    def "the app's page #path is served by the Angular index page"() {
        when:
        def response = mvc.perform(get(path)).andReturn().response

        then:
        response.forwardedUrl == '/index.html'

        where:
        path << ['/self-service', '/admin', '/admin/products/new', '/admin/products/12/edit', '/admin/settings',
                 '/monitoring', '/monitoring/pipelines/7', '/beadle', '/beadle/admin/products/3', '/products',
                 '/products/12/edit', '/settings']
    }

    def "other paths are left to the API and the static resources"() {
        expect:
        mvc.perform(get(path)).andReturn().response.status == 404

        where:
        path << ['/api/unknown', '/favicon.ico', '/index.htm']
    }
}
