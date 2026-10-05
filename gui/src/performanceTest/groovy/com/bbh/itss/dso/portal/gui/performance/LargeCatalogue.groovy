package com.bbh.itss.dso.portal.gui.performance

import com.bbh.itss.dso.portal.gui.support.ApiData
import com.bbh.itss.dso.portal.gui.support.StubApi
import com.bbh.itss.dso.portal.gui.support.StubResponse

class LargeCatalogue {

    static final int PRODUCTS = 25
    static final int SERVICES = 16
    static final Map<String, String> TYPES = [FULL    : 'devSecOpsPipeline', SECURITY: 'devSecOpsSecurityPipeline',
                                              EXTENDED: 'devSecOpsExtendedPipeline', SAST: 'devSecOpsSASTScanningPipeline']
    static final int PIPELINES = SERVICES * TYPES.size()

    private final Map product = StubApi.fixture('product-2.json') as Map
    private final Map pipeline = (StubApi.fixture('product-2-pipelines.json') as List<Map>)[0].pipelines[0] as Map
    private final Map lastRun = (StubApi.fixture('monitoring-product-2.json') as Map).pipelines[0].lastRun as Map
    private final Map evidenceRun = (StubApi.fixture('evidence-product-1.json') as Map).services[0].pipelines[0].run as Map
    private final Map<String, StubResponse> responses = [:]

    LargeCatalogue() {
        def summaries = []
        def overview = []
        (1..PRODUCTS).each { int id ->
            def facts = [code       : "CAT$id".toString(), name: String.format('Catalogue Product %02d', id),
                         description: "Generated product $id of the performance catalogue".toString(), ownerTeam: product.ownerTeam]
            def services = (0..<SERVICES).collect { int index -> service(id, index) }
            def pipelines = services.collect { Map service -> TYPES.keySet().withIndex().collect { String type, int index -> pipelineOf(id, facts, service, type, index) } }
            def all = pipelines.flatten() as List<Map>
            def counts = all.countBy { status(it) }
            def overall = ['FAILURE', 'UNSTABLE', 'SUCCESS'].find { it in counts }
            summaries << facts + [id: id, serviceCount: SERVICES, pipelineCount: PIPELINES, activePipelineCount: all.count { it.enabled },
                                  updatedAt: product.updatedAt]
            overview << facts + [productId: id, serviceCount: SERVICES, pipelineCount: PIPELINES, overall: overall,
                                 statusCounts: counts, lastRunAt: lastRun.time]
            store("/api/products/$id", product + facts + [id: id, services: services])
            store("/api/products/$id/pipelines", [services, pipelines].transpose().collect { Map service, List<Map> own -> ApiData.servicePipelines(service, own) })
            store("/api/monitoring/products/$id", facts + [productId: id, overall: overall, metricsError: null, pipelines: all.collect {
                [pipeline: it, status: status(it), lastRun: lastRun + [result: it.enabled ? status(it) : 'SUCCESS']]
            }])
            store("/api/evidence/products/$id", facts + [productId: id, contactEmail: product.contactEmail, metricsError: null,
                                                         services: [services, pipelines].transpose().collect { Map service, List<Map> own -> serviceEvidence(service, own) }])
        }
        store('/api/products', summaries)
        store('/api/monitoring/products', [products: overview, metricsError: null])
    }

    void serve(StubApi api) {
        responses.each { path, response -> api.respond('GET', path, response) }
    }

    private void store(String path, Object body) {
        responses[path] = StubResponse.json(body)
    }

    private Map service(int productId, int index) {
        def template = (product.services as List<Map>)[index % (product.services as List).size()]
        def name = "${template.name}-${index + 1}".toString()
        template + [id     : (productId - 1) * SERVICES + index + 1, name: name,
                    sonar  : (template.sonar as Map) + [projectKey: "cat$productId-$name".toString()],
                    metrics: (template.metrics as Map) + [influxProject: "CAT$productId-$name".toString()]]
    }

    private Map pipelineOf(int productId, Map facts, Map service, String type, int index) {
        def id = ((service.id as int) - 1) * TYPES.size() + index + 1
        def job = "DevSecOps/${facts.code}/${service.name}-${type.toLowerCase()}".toString()
        pipeline + [id                 : id, productId: productId, productCode: facts.code, productName: facts.name,
                    serviceId          : service.id, serviceName: service.name, type: type, entryPoint: TYPES[type],
                    securityPipelineJob: null, jenkinsJob: job, jenkinsJobUrl: "https://jenkins.bbh.com/job/${job.replace('/', '/job/')}/".toString(),
                    enabled            : id % 11 != 0, activeKey: id % 11 ? ApiData.activeKey(id, ApiData.keyValue(id)) : null,
                    influxProjectTag   : "${facts.code}-${service.name}-${type.toLowerCase()}".toString()]
    }

    private static String status(Map pipeline) {
        def id = pipeline.id as int
        !pipeline.enabled ? 'DISABLED' : id % 5 == 0 ? 'FAILURE' : id % 3 == 0 ? 'UNSTABLE' : 'SUCCESS'
    }

    private Map serviceEvidence(Map service, List<Map> pipelines) {
        [serviceId           : service.id, name: service.name, description: service.description,
         repositoryUrl       : service.scm.repositoryUrl, artifactName: service.deployment.artifactName,
         appScanApplicationId: service.appScan.applicationId, sonarProjectKey: service.sonar.projectKey,
         nexusIqApplication  : service.nexusIq.application,
         pipelines           : pipelines.collect { [pipelineId: it.id, type: it.type, enabled: it.enabled, jenkinsJobUrl: it.jenkinsJobUrl,
                                                    status    : status(it), run: evidenceRun] }]
    }
}
