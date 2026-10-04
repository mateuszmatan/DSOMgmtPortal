package com.bbh.itss.dso.portal.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonSlurper
import groovy.transform.Canonical

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FakeInfluxDb implements AutoCloseable {

    static final List<String> RUN_COLUMNS = ['_time', 'project', 'env', 'variant', 'result', 'branch', 'build',
                                             'duration_s', 'commit', 'job', 'stages_total', 'passed', 'warned',
                                             'failed', 'blocked', 'skipped']
    static final List<String> DORA_COLUMNS = ['_time', 'project', 'env', 'variant', 'change_failure', 'deployment',
                                              'duration_s', 'lead_time_s']

    private static FakeInfluxDb sharedInstance

    final List<Request> requests = new CopyOnWriteArrayList<>()

    private final List<Run> runs = new CopyOnWriteArrayList<>()
    private final List<Map<String, String>> points = new CopyOnWriteArrayList<>()
    private final HttpServer server
    private final ExecutorService executor = Executors.newFixedThreadPool(16, { Runnable task ->
        Thread thread = new Thread(task, 'fake-influxdb')
        thread.daemon = true
        thread
    })
    private volatile String fixedAnswer
    private volatile int status = 200

    private FakeInfluxDb() {
        server = HttpServer.create(new InetSocketAddress(InetAddress.loopbackAddress, 0), 0)
        server.executor = executor
        server.createContext('/api/v2/query') { HttpExchange exchange -> handle(exchange) }
        server.start()
    }

    static FakeInfluxDb start() {
        new FakeInfluxDb()
    }

    static synchronized FakeInfluxDb shared() {
        if (sharedInstance == null) {
            sharedInstance = start()
            Runtime.runtime.addShutdownHook(new Thread({ sharedInstance.close() }))
        }
        sharedInstance
    }

    String getUrl() {
        "http://127.0.0.1:${server.address.port}"
    }

    void respondWith(String csv) {
        fixedAnswer = csv
    }

    void failWith(int status) {
        this.status = status
    }

    void addRun(Map args) {
        String result = args.result ?: 'SUCCESS'
        runs << new Run(project: args.project, env: args.env ?: 'test', variant: args.variant ?: 'full',
                time: args.time as Instant ?: Instant.now(), result: result, branch: args.branch ?: 'develop',
                build: args.build as Long ?: runs.size() + 1, durationSeconds: args.durationSeconds as Long ?: 600,
                deployment: args.containsKey('deployment') ? args.deployment : result == 'SUCCESS',
                leadTimeSeconds: args.leadTimeSeconds as Long ?: 3600)
    }

    void addPoint(Map args) {
        Map<String, String> row = [_measurement: args.measurement as String, project: args.project as String,
                                   env         : (args.env ?: 'test') as String,
                                   _time       : ((args.time ?: Instant.now()) as Instant).toString()]
        args.findAll { !(it.key in ['measurement', 'project', 'env', 'time']) }
                .each { name, value -> row[name as String] = value as String }
        points << row
    }

    void reset() {
        runs.clear()
        points.clear()
        requests.clear()
        fixedAnswer = null
        status = 200
    }

    @Override
    void close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private void handle(HttpExchange exchange) {
        try {
            Map body = new JsonSlurper().parse(exchange.requestBody) as Map
            requests << new Request(method: exchange.requestMethod, path: exchange.requestURI.path,
                    query: exchange.requestURI.query, authorization: exchange.requestHeaders.getFirst('Authorization'),
                    contentType: exchange.requestHeaders.getFirst('Content-Type'),
                    accept: exchange.requestHeaders.getFirst('Accept'), body: body)
            String answer = status == 200 ? (fixedAnswer ?: answer(body.query as String))
                    : '{"code":"unauthorized","message":"unauthorized access"}'
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add('Content-Type', status == 200 ? 'text/csv; charset=utf-8' : 'application/json')
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length)
            exchange.responseBody.withStream { it.write(bytes) }
        } finally {
            exchange.close()
        }
    }

    private String answer(String flux) {
        if (flux.startsWith('buckets()')) {
            return ',result,table,name\r\n,_result,0,DORA-metrics\r\n'
        }
        if (flux.contains('"security_findings"')) {
            return evidencePoints(flux)
        }
        if (flux.contains('last(column: "_time")')) {
            return latestRuns(flux)
        }
        if (flux.contains('"pipeline_run"')) {
            return recentRuns(flux)
        }
        if (flux.contains('"dora"')) {
            return doraPoints(flux)
        }
        return ''
    }

    private String latestRuns(String flux) {
        Set<String> projects = (flux =~ /set: \[(.*?)]/)[0][1].findAll(/"([^"]*)"/) { all, value -> value } as Set
        Instant since = Instant.now() - Duration.ofDays(days(flux))
        def newest = runs.findAll { it.project in projects && it.time.isAfter(since) }
                .groupBy { [it.project, it.env] }
                .values()
                .collect { it.max { run -> run.time } }
        csv(RUN_COLUMNS, newest.withIndex().collect { run, table -> [table, runValues(run)] })
    }

    private String recentRuns(String flux) {
        int limit = (flux =~ /limit\(n: (\d+)\)/)[0][1] as int
        def selected = selectedRuns(flux).sort { -it.time.toEpochMilli() }.take(limit)
        csv(RUN_COLUMNS, selected.collect { [0, runValues(it)] })
    }

    private String doraPoints(String flux) {
        def selected = selectedRuns(flux).sort { it.time }
        csv(DORA_COLUMNS, selected.collect { run ->
            [0, [run.time.toString(), run.project, run.env, run.variant, run.result == 'SUCCESS' ? '0' : '1',
                 run.deployment ? '1' : '0', run.durationSeconds as String, run.leadTimeSeconds as String]]
        })
    }

    private String evidencePoints(String flux) {
        def sets = (flux =~ /set: \[(.*?)]/).collect { it[1].findAll(/"([^"]*)"/) { all, value -> value } as Set }
        def range = (flux =~ /range\(start: time\(v: "([^"]+)"\), stop: time\(v: "([^"]+)"\)\)/)[0]
        Instant start = Instant.parse(range[1])
        Instant stop = Instant.parse(range[2])
        def selected = points.findAll { point ->
            Instant at = Instant.parse(point._time)
            point._measurement in sets[0] && point.project in sets[1] && !at.isBefore(start) && at.isBefore(stop)
        }
        selected.withIndex().collect { point, table ->
            csv(point.keySet() as List, [[table, point.values().collect { csvCell(it) }]])
        }.join('\r\n')
    }

    private static String csvCell(String value) {
        value != null && (value.contains(',') || value.contains('"')) ? '"' + value.replace('"', '""') + '"' : (value ?: '')
    }

    private List<Run> selectedRuns(String flux) {
        def filter = (flux =~ /r\.project == "([^"]*)" and r\.env == "([^"]*)"/)[0]
        Instant since = Instant.now() - Duration.ofDays(days(flux))
        runs.findAll { it.project == filter[1] && it.env == filter[2] && it.time.isAfter(since) }
    }

    private static long days(String flux) {
        (flux =~ /range\(start: -(\d+)d\)/)[0][1] as long
    }

    private static List<String> runValues(Run run) {
        [run.time.toString(), run.project, run.env, run.variant, run.result, run.branch, run.build as String,
         run.durationSeconds as String, 'a1b2c3d4e5f6', "${run.project}/${run.variant}".toString(), '12',
         run.result == 'SUCCESS' ? '12' : '11', run.result == 'UNSTABLE' ? '1' : '0', run.result == 'FAILURE' ? '1' : '0',
         '0', '0']
    }

    private static String csv(List<String> columns, List<List> tablesAndValues) {
        if (tablesAndValues.isEmpty()) {
            return ''
        }
        def lines = [',result,table,' + columns.join(',')]
        tablesAndValues.each { table, values -> lines << ",_result,$table," + values.join(',') }
        lines.join('\r\n') + '\r\n'
    }

    @Canonical
    static class Request {
        String method
        String path
        String query
        String authorization
        String contentType
        String accept
        Map body
    }

    @Canonical
    static class Run {
        String project
        String env
        String variant
        Instant time
        String result
        String branch
        Long build
        Long durationSeconds
        boolean deployment
        Long leadTimeSeconds
    }
}
