package com.bbh.dso.portal.dsoconfig;

import com.bbh.dso.portal.catalog.Product;
import com.bbh.dso.portal.catalog.ScanPatterns;
import com.bbh.dso.portal.catalog.ServiceDefinition;
import com.bbh.dso.portal.pipeline.Pipeline;
import com.bbh.dso.portal.pipeline.PipelineType;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders portal data in the shape of the config.yaml the DevSecOps library reads today, so the library
 * can switch from the file in the repository to the portal without changing how it interprets the values.
 * <p>
 * Each service section is layered: BBH-wide defaults first, then the service's additional YAML, then the
 * values that have their own column in the portal, which always win.
 */
@Component
public class DsoConfigBuilder {

    private static final List<String> KEY_ORDER = List.of(
            "appId", "buildTool", "deployTarget", "sourceDir", "javaPath", "buildToolAutoSetup", "includedDirs",
            "excludedDirs", "appName", "artifactName", "baseArtifactName", "jenkins", "asoc", "influx", "coverage",
            "tools", "sast", "sca", "dast", "build", "delivery", "scm", "goldenFix", "tests", "deploy", "flutter");

    private final DsoDefaultsProperties defaults;
    private final AdditionalConfigValidator additionalConfig;

    public DsoConfigBuilder(DsoDefaultsProperties defaults, AdditionalConfigValidator additionalConfig) {
        this.defaults = defaults;
        this.additionalConfig = additionalConfig;
    }

    /** The configuration of every service of a product, as one config.yaml. */
    public Map<String, Object> productConfig(Product product) {
        Map<String, Object> projects = new LinkedHashMap<>();
        for (ServiceDefinition service : product.getServices()) {
            projects.put(service.getName(), serviceConfig(service));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("projects", projects);
        return root;
    }

    /**
     * What a pipeline receives for its key: the pipeline settings that today live in the Jenkinsfile, plus
     * the {@code projects:} section with the pipeline's service.
     */
    public Map<String, Object> pipelineConfig(Pipeline pipeline) {
        ServiceDefinition service = pipeline.getService();
        Map<String, Object> pipelineSection = new LinkedHashMap<>();
        pipelineSection.put("type", pipeline.getType().variant());
        pipelineSection.put("entryPoint", pipeline.getType().entryPoint());
        pipelineSection.put("product", service.getProduct().getCode());
        pipelineSection.put("projectNames", service.getName());
        pipelineSection.put("agentNames", pipeline.agentLabelList());

        Map<String, Object> serviceSection = serviceConfig(service);
        if (pipeline.getType() == PipelineType.SECURITY && pipeline.getExtendedPipelineJob() != null) {
            deepMerge(serviceSection, Map.of("jenkins", Map.of("pipeline",
                    Map.of("extendedPipeline", pipeline.getExtendedPipelineJob()))));
            serviceSection = canonicalOrder(serviceSection);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("pipeline", pipelineSection);
        root.put("projects", Map.of(service.getName(), serviceSection));
        return root;
    }

    public Map<String, Object> serviceConfig(ServiceDefinition service) {
        Map<String, Object> config = defaultsSection();
        deepMerge(config, additionalConfig.parse(service.getAdditionalConfig()));
        deepMerge(config, columnsSection(service));
        return canonicalOrder(config);
    }

    public String toYaml(Map<String, Object> config) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setPrettyFlow(true);
        options.setWidth(160);
        return new Yaml(new Representer(options), options).dump(config);
    }

    private Map<String, Object> defaultsSection() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("asoc", map("url", defaults.asocUrl()));
        config.put("influx", map("url", defaults.influxWriteUrl(), "credentialsId", defaults.influxCredentialsId()));
        config.put("tools", map(
                "sonar", map("serverUrl", defaults.sonarServerUrl()),
                "nexusIq", map("serverUrl", defaults.nexusIqServerUrl(), "credentialsId", defaults.nexusIqCredentialsId())));
        return config;
    }

    private static Map<String, Object> columnsSection(ServiceDefinition service) {
        Product product = service.getProduct();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("appId", service.getAppScanAppId());
        config.put("buildTool", service.getBuildTool().configValue());
        config.put("deployTarget", service.getDeployTarget().configValue());
        config.put("sourceDir", service.getSourceDir());
        putIfPresent(config, "javaPath", service.getJavaPath());
        if (service.isBuildToolAutoSetup()) {
            config.put("buildToolAutoSetup", true);
        }
        putIfPresent(config, "appName", service.getAppName());
        putIfPresent(config, "artifactName", service.getArtifactName());

        Map<String, Object> asoc = map("keyId", product.getAsocKeyId());
        putIfPresent(asoc, "token", product.getAsocSecretCredentialsId());
        config.put("asoc", asoc);

        config.put("influx", map("enabled", service.isMetricsEnabled(), "project", service.getInfluxProject(),
                "env", service.getInfluxEnv()));

        Map<String, Object> sonar = new LinkedHashMap<>();
        putIfPresent(sonar, "projectName", service.getSonarProjectName());
        putIfPresent(sonar, "projectKey", service.getSonarProjectKey());
        Map<String, Object> nexusIq = new LinkedHashMap<>();
        putIfPresent(nexusIq, "application", service.getNexusIqApplication());
        List<String> patterns = ScanPatterns.split(service.getNexusIqScanPatterns());
        if (!patterns.isEmpty()) {
            nexusIq.put("scanPatterns", patterns);
        }
        config.put("tools", map("sonar", sonar, "nexusIq", nexusIq));

        if (service.getSastScanName() != null) {
            config.put("sast", map("scanName", service.getSastScanName()));
        }
        Map<String, Object> dast = map("enabled", service.isDastEnabled());
        putIfPresent(dast, "targetUrl", service.getDastTargetUrl());
        putIfPresent(dast, "presenceId", service.getDastPresenceId());
        config.put("dast", dast);

        Map<String, Object> bitbucket = new LinkedHashMap<>();
        putIfPresent(bitbucket, "url", service.getRepositoryUrl());
        putIfPresent(bitbucket, "credentialsId", service.getBitbucketCredentialsId());
        if (!bitbucket.isEmpty()) {
            config.put("scm", map("bitbucket", bitbucket));
        }
        config.put("goldenFix", map("enabled", service.isGoldenFixEnabled()));
        return config;
    }

    /** Puts the keys in the order of the config.yaml reference, unknown keys last, so the output reads like the template. */
    private static Map<String, Object> canonicalOrder(Map<String, Object> config) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String key : KEY_ORDER) {
            if (config.containsKey(key)) {
                ordered.put(key, config.get(key));
            }
        }
        config.forEach(ordered::putIfAbsent);
        return ordered;
    }

    /** Merges {@code override} into {@code base}; nested maps merge key by key, any other value replaces. */
    @SuppressWarnings("unchecked")
    static void deepMerge(Map<String, Object> base, Map<String, ?> override) {
        for (Map.Entry<String, ?> entry : override.entrySet()) {
            Object current = base.get(entry.getKey());
            if (current instanceof Map<?, ?> currentMap && entry.getValue() instanceof Map<?, ?> overrideMap) {
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) currentMap);
                deepMerge(merged, (Map<String, ?>) overrideMap);
                base.put(entry.getKey(), merged);
            } else if (entry.getValue() instanceof Map<?, ?> overrideMap) {
                Map<String, Object> copy = new LinkedHashMap<>();
                deepMerge(copy, (Map<String, ?>) overrideMap);
                base.put(entry.getKey(), copy);
            } else {
                base.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
