package com.bbh.itss.dso.portal.domain.dsoconfig;

import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DsoConfigBuilder {

    static final List<String> KEY_ORDER = List.of(
            "appId", "buildTool", "deployTarget", "sourceDir", "javaPath", "buildToolAutoSetup", "includedDirs",
            "excludedDirs", "appscanPath", "appName", "artifactName", "baseArtifactName", "jenkins", "asoc", "influx",
            "coverage", "tools", "sast", "sca", "dast", "build", "delivery", "scm", "goldenFix", "tests", "deploy",
            "flutter");

    private final GlobalSettingsValues global;

    public DsoConfigBuilder(GlobalSettingsValues global) {
        this.global = Objects.requireNonNull(global, "the configuration is built on the global settings");
    }

    public Map<String, Object> productConfig(Product product) {
        Map<String, Object> projects = new LinkedHashMap<>();
        product.services().forEach(service ->
                projects.put(service.name(), serviceTree(product, service).toMap(KEY_ORDER)));
        return Map.of("projects", projects);
    }

    public Map<String, Object> pipelineConfig(Product product, Service service, Pipeline pipeline) {
        PipelineSettings pipelineSettings = pipeline.settings();
        ConfigTree serviceTree = serviceTree(product, service);
        if (pipeline.type() == PipelineType.SECURITY) {
            serviceTree.set("jenkins.pipeline.extendedPipeline", pipelineSettings.extendedPipelineJob());
        }

        Map<String, Object> pipelineSection = new LinkedHashMap<>();
        pipelineSection.put("type", pipeline.type().variant());
        pipelineSection.put("entryPoint", pipeline.type().entryPoint());
        pipelineSection.put("product", product.code());
        pipelineSection.put("projectNames", service.name());
        pipelineSection.put("agentNames", pipelineSettings.agentLabels());
        if (pipelineSettings.securityPipelineJob() != null) {
            pipelineSection.put("securityPipeline", pipelineSettings.securityPipelineJob());
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("pipeline", pipelineSection);
        root.put("platform", global.platform().toConfig());
        root.put("defaults", global.defaultsConfig());
        root.put("projects", Map.of(service.name(), serviceTree.toMap(KEY_ORDER)));
        return root;
    }

    public Map<String, Object> globalConfig() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("platform", global.platform().toConfig());
        root.put("defaults", global.defaultsConfig());
        return root;
    }

    private ConfigTree serviceTree(Product product, Service service) {
        ConfigTree tree = new ConfigTree();
        global.platform().writeProjectDefaults(tree);
        product.writeConfig(service, tree);
        global.deployment().fillIn(tree);
        return tree;
    }
}
