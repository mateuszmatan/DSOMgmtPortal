package com.bbh.itss.dso.portal.gui.support

class ProductStore {

    static final String SAVED_AT = '2026-10-05T10:00:00Z'

    private final StubApi api
    private final int id
    private Map product
    private List<Map> services
    private long nextServiceId = 100
    private long nextPipelineId = 100
    private long nextKeyId = 100

    final Map<String, String> generatedKeys = [:]

    private ProductStore(StubApi api, int id, Map product, List<Map> services) {
        this.api = api
        this.id = id
        this.product = product
        this.services = services
    }

    static ProductStore recorded(StubApi api, int id) {
        new ProductStore(api, id, StubApi.fixture("product-${id}.json") as Map,
                StubApi.fixture("product-${id}-pipelines.json") as List<Map>).serve()
    }

    static ProductStore created(StubApi api, int id) {
        def store = new ProductStore(api, id, null, [])
        api.on('POST', '/api/products') { RecordedRequest request ->
            StubResponse.json(store.save(request.json() as Map, request.params().pipelineType), 201)
        }
        store.serve()
    }

    Map getProduct() {
        product
    }

    List<Map> getServices() {
        services
    }

    private ProductStore serve() {
        def path = "/api/products/$id"
        api.get(path) {
            product ? StubResponse.json(product) : StubResponse.problem(404, 'Not Found', "Product $id was not found")
        }
        api.get("$path/pipelines") { StubResponse.json(services) }
        api.on('PUT', path) { RecordedRequest request ->
            def body = request.json() as Map
            body.version == product.version ? StubResponse.json(save(body, request.params().pipelineType))
                    : StubResponse.problem(409, 'Conflict', 'The product was changed by someone else; reload it and try again')
        }
        this
    }

    private synchronized Map save(Map request, String type) {
        def saved = request.services.collect { Map service ->
            def stored = service.id == null ? service + [id: nextServiceId++] : service
            stored + [flutter: stored.flutter ?: ApiData.noFlutterSettings()]
        }
        def previous = product
        product = request + [id       : id, version: previous == null ? 0 : (previous.version as int) + 1,
                             createdAt: previous?.createdAt ?: SAVED_AT, updatedAt: SAVED_AT, services: saved]
        services = saved.collect { Map service -> ApiData.servicePipelines(service, pipelinesOf(service, type)) }
        product
    }

    private List<Map> pipelinesOf(Map service, String type) {
        def existing = ((services.find { it.serviceId == service.id }?.pipelines ?: []) as List<Map>)
                .collect { it + [serviceName: service.name] }
        def wanted = type ?: (existing ? null : 'FULL')
        if (wanted == null || existing.any { it.type == wanted }) {
            return existing
        }
        def value = ApiData.keyValue(nextKeyId)
        generatedKeys[service.name as String] = value
        def key = ApiData.activeKey(nextKeyId++, value, SAVED_AT)
        existing + [ApiData.newPipeline(nextPipelineId++, product, service, key, wanted)]
    }
}
