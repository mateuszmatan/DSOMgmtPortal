package com.bbh.itss.dso.portal.gui.performance

import com.bbh.itss.dso.portal.gui.support.ApiData
import com.bbh.itss.dso.portal.gui.support.RecordedRequest
import com.bbh.itss.dso.portal.gui.support.StubApi
import com.bbh.itss.dso.portal.gui.support.StubResponse
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class LargeCatalogue {

    static final int PRODUCTS = 25
    static final int SERVICES_PER_PRODUCT = 16
    static final List<String> PIPELINE_TYPES = ['FULL', 'SECURITY', 'EXTENDED', 'SAST']
    static final Map<String, String> ENTRY_POINTS = [FULL    : 'devSecOpsPipeline', SECURITY: 'devSecOpsSecurityPipeline',
                                                     EXTENDED: 'devSecOpsExtendedPipeline', SAST: 'devSecOpsSASTScanningPipeline']
    static final Map<String, String> JOB_SUFFIXES = [FULL: 'full', SECURITY: 'security', EXTENDED: 'extended', SAST: 'sast']
    static final String JENKINS = 'https://jenkins.bbh.com'
    static final String CHANGED_AT = '2026-10-05T08:00:00Z'
    static final String RUN_AT = '2026-10-05T07:30:00Z'
    static final List<String> TEAMS = ['Payments Engineering', 'Technology Architecture', 'Custody Platforms',
                                       'Fund Accounting', 'Client Reporting']
    static final List<String> MEASURED_PAYLOADS = ['/api/products', '/api/products/1', '/api/products/1/pipelines',
                                                   '/api/monitoring/products', '/api/monitoring/products/1',
                                                   '/api/evidence/products/1']

    private final Map product
    private final List<Map> serviceTemplates
    private final Map pipelineTemplate
    private final Map runTemplate
    private final Map evidenceRunTemplate
    private final Map<String, StubResponse> responses = [:]
    private final Map<String, Integer> sizes = [:]

    private LargeCatalogue() {
        product = StubApi.fixture('product-2.json') as Map
        serviceTemplates = product.services as List<Map>
        pipelineTemplate = ((StubApi.fixture('product-2-pipelines.json') as List<Map>)[0].pipelines as List<Map>)[0]
        runTemplate = ((StubApi.fixture('monitoring-product-2.json') as Map).pipelines as List<Map>)[0].lastRun as Map
        evidenceRunTemplate = (((StubApi.fixture('evidence-product-1.json') as Map).services as List<Map>)[0]
                .pipelines as List<Map>)[0].run as Map
    }

    static LargeCatalogue generate() {
        new LargeCatalogue().build()
    }

    int getServiceCount() {
        PRODUCTS * SERVICES_PER_PRODUCT
    }

    int getPipelineCount() {
        serviceCount * PIPELINE_TYPES.size()
    }

    int getPipelinesPerProduct() {
        SERVICES_PER_PRODUCT * PIPELINE_TYPES.size()
    }

    Map<String, Integer> getPayloadSizes() {
        MEASURED_PAYLOADS.collectEntries { [(it): sizes[it]] }
    }

    String productName(int productId) {
        String.format('Catalogue Product %02d', productId)
    }

    String serviceName(int index) {
        "${serviceTemplates[index % serviceTemplates.size()].name}-${index + 1}".toString()
    }

    String code(int productId) {
        String.format('CAT%02d', productId)
    }

    void serve(StubApi api) {
        ['/api/products', '/api/products/\\d+', '/api/products/\\d+/pipelines', '/api/monitoring/products',
         '/api/monitoring/products/\\d+', '/api/evidence/products/\\d+'].each { pattern ->
            api.get(pattern) { RecordedRequest request ->
                responses[request.path] ?: StubResponse.problem(404, 'Not Found', "No catalogue entry for ${request.path}")
            }
        }
    }

    private LargeCatalogue build() {
        def summaries = []
        def health = []
        (1..PRODUCTS).each { int productId ->
            def services = (0..<SERVICES_PER_PRODUCT).collect { int index -> service(productId, index) }
            def pipelines = services.collect { Map service -> pipelinesOf(productId, service) }
            def flat = pipelines.flatten() as List<Map>
            summaries << summary(productId, flat)
            health << productHealth(productId, flat)
            store("/api/products/$productId", productOf(productId, services))
            store("/api/products/$productId/pipelines", [services, pipelines].transpose().collect { pair ->
                ApiData.servicePipelines(pair[0] as Map, pair[1] as List<Map>)
            })
            store("/api/monitoring/products/$productId", productMonitoring(productId, flat))
            store("/api/evidence/products/$productId", productEvidence(productId, services, pipelines))
        }
        store('/api/products', summaries)
        store('/api/monitoring/products', [products: health, metricsError: null])
        this
    }

    private void store(String path, Object body) {
        def json = JsonOutput.toJson(body)
        responses[path] = new StubResponse(status: 200, contentType: 'application/json', body: json)
        if (path in MEASURED_PAYLOADS) {
            sizes[path] = json.getBytes('UTF-8').length
        }
    }

    private static String description(int productId) {
        "Generated product $productId of the performance catalogue".toString()
    }

    private static String team(int productId) {
        TEAMS[productId % TEAMS.size()]
    }

    private Map productOf(int productId, List<Map> services) {
        product + [id         : productId, code: code(productId), name: productName(productId),
                   description: description(productId),
                   ownerTeam  : team(productId),
                   contactEmail: "catalogue-${productId}@bbh.com".toString(),
                   appScan    : [keyId: String.format('bbh_%08x-0000-4000-8000-%012x', productId, productId), secretCredentialsId: null],
                   version    : productId % 3, createdAt: CHANGED_AT, updatedAt: CHANGED_AT, services: services]
    }

    private Map service(int productId, int index) {
        def template = copy(serviceTemplates[index % serviceTemplates.size()])
        def id = (productId - 1) * SERVICES_PER_PRODUCT + index + 1
        def name = serviceName(index)
        def slug = "cat${productId}-${name}".toString()
        template.id = id
        template.name = name
        (template.appScan as Map).applicationId = String.format('%08x-1111-4a5b-8c9d-%012x', productId, id)
        def sonar = template.sonar as Map
        if (sonar.projectKey) {
            sonar.projectKey = slug
            sonar.projectName = slug.toUpperCase()
        }
        def nexusIq = template.nexusIq as Map
        if (nexusIq.application) {
            nexusIq.application = slug
        }
        (template.metrics as Map).influxProject = "${code(productId)}-${name}".toString()
        (template.scm as Map).repositoryUrl = "https://bitbucket.bbh.com/projects/CAT/repos/${slug}".toString()
        def deployment = template.deployment as Map
        if (deployment.appName) {
            deployment.appName = slug
            deployment.artifactName = "${slug}.jar".toString()
        }
        template
    }

    private List<Map> pipelinesOf(int productId, Map service) {
        PIPELINE_TYPES.withIndex().collect { String type, int index ->
            def id = ((service.id as int) - 1) * PIPELINE_TYPES.size() + index + 1
            def job = "DevSecOps/${code(productId)}/${service.name}-${JOB_SUFFIXES[type]}".toString()
            def enabled = id % 11 != 0
            def value = ApiData.keyValue(id)
            pipelineTemplate + [id                 : id, productId: productId, productCode: code(productId),
                                productName        : productName(productId), serviceId: service.id, serviceName: service.name,
                                type               : type, entryPoint: ENTRY_POINTS[type],
                                extendedPipelineJob: type == 'SECURITY' ? job.replace('-security', '-extended') : null,
                                securityPipelineJob: type == 'EXTENDED' ? job.replace('-extended', '-security') : null,
                                jenkinsJob         : job, jenkinsJobUrl: jobUrl(job), enabled: enabled,
                                activeKey          : enabled ? ApiData.activeKey(id, value, CHANGED_AT) : null,
                                influxProjectTag   : "${code(productId)}-${service.name}${type == 'FULL' ? '' : JOB_SUFFIXES[type]}".toString(),
                                createdAt          : CHANGED_AT, updatedAt: CHANGED_AT, keys: null]
        }
    }

    private static String jobUrl(String job) {
        JENKINS + '/job/' + job.split('/').join('/job/') + '/'
    }

    private static String status(Map pipeline) {
        def id = pipeline.id as int
        !pipeline.enabled ? 'DISABLED' : id % 5 == 0 ? 'FAILURE' : id % 3 == 0 ? 'UNSTABLE' : 'SUCCESS'
    }

    private static String overall(List<Map> pipelines) {
        def statuses = pipelines.collect { status(it) }
        ['FAILURE', 'UNSTABLE', 'SUCCESS'].find { it in statuses } ?: 'NO_DATA'
    }

    private Map summary(int productId, List<Map> pipelines) {
        [id                 : productId, code: code(productId), name: productName(productId),
         description        : description(productId),
         ownerTeam          : team(productId), serviceCount: SERVICES_PER_PRODUCT,
         pipelineCount      : pipelines.size(), activePipelineCount: pipelines.count { it.enabled },
         updatedAt          : CHANGED_AT]
    }

    private Map productHealth(int productId, List<Map> pipelines) {
        [productId  : productId, code: code(productId), name: productName(productId), ownerTeam: team(productId),
         serviceCount: SERVICES_PER_PRODUCT, pipelineCount: pipelines.size(), overall: overall(pipelines),
         statusCounts: pipelines.countBy { status(it) }, lastRunAt: RUN_AT]
    }

    private Map run(Map pipeline) {
        def job = "${pipeline.productCode}-${pipeline.serviceName}/${JOB_SUFFIXES[pipeline.type]}".toString()
        def build = 40 + ((pipeline.id as int) % 60)
        def result = status(pipeline) == 'DISABLED' ? 'SUCCESS' : status(pipeline)
        runTemplate + [time: RUN_AT, result: result, build: build, job: job, buildUrl: "${jobUrl(job)}${build}/".toString()]
    }

    private Map productMonitoring(int productId, List<Map> pipelines) {
        [productId  : productId, code: code(productId), name: productName(productId),
         description: description(productId),
         ownerTeam  : team(productId), overall: overall(pipelines),
         pipelines  : pipelines.collect { [pipeline: it, status: status(it), lastRun: run(it)] }, metricsError: null]
    }

    private Map productEvidence(int productId, List<Map> services, List<List<Map>> pipelines) {
        [productId   : productId, code: code(productId), name: productName(productId),
         description : description(productId),
         ownerTeam   : team(productId), contactEmail: "catalogue-${productId}@bbh.com".toString(),
         services    : [services, pipelines].transpose().collect { pair -> serviceEvidence(pair[0] as Map, pair[1] as List<Map>) },
         metricsError: null]
    }

    private Map serviceEvidence(Map service, List<Map> pipelines) {
        [serviceId           : service.id, name: service.name, description: service.description,
         repositoryUrl       : (service.scm as Map).repositoryUrl, artifactName: (service.deployment as Map).artifactName,
         appScanApplicationId: (service.appScan as Map).applicationId, sonarProjectKey: (service.sonar as Map).projectKey,
         nexusIqApplication  : (service.nexusIq as Map).application,
         pipelines           : pipelines.collect { pipelineEvidence(it) }]
    }

    private Map pipelineEvidence(Map pipeline) {
        def lastRun = run(pipeline)
        def build = (evidenceRunTemplate.build as Map) + [number: lastRun.build, job: lastRun.job, url: lastRun.buildUrl,
                                                         reportUrl    : "${lastRun.buildUrl}Pipeline_20Report/".toString(),
                                                         testReportUrl: "${lastRun.buildUrl}testReport/".toString(),
                                                         artifactsUrl : "${lastRun.buildUrl}artifact/".toString()]
        [pipelineId: pipeline.id, type: pipeline.type, enabled: pipeline.enabled, jenkinsJobUrl: pipeline.jenkinsJobUrl,
         status    : status(pipeline), run: evidenceRunTemplate + [build: build]]
    }

    private static Map copy(Map value) {
        new JsonSlurper().parseText(JsonOutput.toJson(value)) as Map
    }
}
