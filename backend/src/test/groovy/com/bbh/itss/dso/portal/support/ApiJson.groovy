package com.bbh.itss.dso.portal.support

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Request bodies of the portal API as the Angular client sends them, with named arguments overriding the
 * defaults, and parsing of the responses.
 */
final class ApiJson {

    static final String APP_ID = Fixtures.APP_ID

    private ApiJson() {
    }

    static Map product(Map overrides = [:]) {
        [code    : 'CERT',
         name    : 'CertScanner',
         appScan : [keyId: 'bbh_key-id', secretCredentialsId: 'hcl-app-scan-account'],
         services: [service()]] + overrides
    }

    static Map service(Map overrides = [:]) {
        [name      : 'gui',
         build     : [tool: 'GRADLE', javaPath: Fixtures.JDK],
         deployment: [target: 'VM'],
         appScan   : [applicationId: APP_ID]] + overrides
    }

    static Map pipeline(Map overrides = [:]) {
        [type: 'FULL', agentLabels: ['linux-agent']] + overrides
    }

    static String toJson(Object body) {
        JsonOutput.toJson(body)
    }

    static Object parse(String json) {
        json ? new JsonSlurper().parseText(json) : null
    }
}
