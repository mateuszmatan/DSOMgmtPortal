package com.bbh.itss.dso.portal.dsoconfig;

import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort;
import com.bbh.itss.dso.portal.catalog.Product;
import com.bbh.itss.dso.portal.catalog.ServiceDefinition;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.settings.MissingGlobalSettingsException;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.pipeline.Pipeline;
import com.bbh.itss.dso.portal.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.pipeline.PipelineType;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DsoConfigBuilder {

    static final List<String> KEY_ORDER = List.of(
            "appId", "buildTool", "deployTarget", "sourceDir", "javaPath", "buildToolAutoSetup", "includedDirs",
            "excludedDirs", "appscanPath", "appName", "artifactName", "baseArtifactName", "jenkins", "asoc", "influx",
            "coverage", "tools", "sast", "sca", "dast", "build", "delivery", "scm", "goldenFix", "tests", "deploy",
            "flutter");

    private final GlobalSettingsRepositoryPort settings;

    public DsoConfigBuilder(GlobalSettingsRepositoryPort settings) {
        this.settings = settings;
    }

    public Map<String, Object> productConfig(Product product) {
        GlobalSettingsValues global = globalSettings();
        Map<String, Object> projects = new LinkedHashMap<>();
        product.getServices().forEach(service -> projects.put(service.getName(), serviceTree(service, global).toMap(KEY_ORDER)));
        return Map.of("projects", projects);
    }

    public Map<String, Object> pipelineConfig(Pipeline pipeline) {
        GlobalSettingsValues global = globalSettings();
        ServiceDefinition service = pipeline.getService();
        PipelineSettings pipelineSettings = pipeline.getSettings();
        ConfigTree serviceTree = serviceTree(service, global);
        if (pipeline.getType() == PipelineType.SECURITY) {
            serviceTree.set("jenkins.pipeline.extendedPipeline", pipelineSettings.extendedPipelineJob());
        }

        Map<String, Object> pipelineSection = new LinkedHashMap<>();
        pipelineSection.put("type", pipeline.getType().variant());
        pipelineSection.put("entryPoint", pipeline.getType().entryPoint());
        pipelineSection.put("product", service.getProduct().getCode());
        pipelineSection.put("projectNames", service.getName());
        pipelineSection.put("agentNames", pipelineSettings.agentLabels());
        if (pipelineSettings.securityPipelineJob() != null) {
            pipelineSection.put("securityPipeline", pipelineSettings.securityPipelineJob());
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("pipeline", pipelineSection);
        root.put("platform", global.platform().toConfig());
        root.put("defaults", global.defaultsConfig());
        root.put("projects", Map.of(service.getName(), serviceTree.toMap(KEY_ORDER)));
        return root;
    }

    public Map<String, Object> globalConfig() {
        GlobalSettingsValues global = globalSettings();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("platform", global.platform().toConfig());
        root.put("defaults", global.defaultsConfig());
        return root;
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

    private GlobalSettingsValues globalSettings() {
        return settings.load().map(GlobalSettings::values).orElseThrow(MissingGlobalSettingsException::new);
    }

    private static ConfigTree serviceTree(ServiceDefinition service, GlobalSettingsValues global) {
        ConfigTree tree = new ConfigTree();
        global.platform().writeProjectDefaults(tree);
        service.writeTo(tree);
        global.deployment().fillIn(tree);
        return tree;
    }
}
