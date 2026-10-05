package com.bbh.itss.dso.portal.dsoconfig;

import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.settings.MissingGlobalSettingsException;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
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
        product.services().forEach(service ->
                projects.put(service.name(), serviceTree(product, service, global).toMap(KEY_ORDER)));
        return Map.of("projects", projects);
    }

    public Map<String, Object> pipelineConfig(Product product, Service service, Pipeline pipeline) {
        GlobalSettingsValues global = globalSettings();
        PipelineSettings pipelineSettings = pipeline.settings();
        ConfigTree serviceTree = serviceTree(product, service, global);
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

    private static ConfigTree serviceTree(Product product, Service service, GlobalSettingsValues global) {
        ConfigTree tree = new ConfigTree();
        global.platform().writeProjectDefaults(tree);
        product.writeConfig(service, tree);
        global.deployment().fillIn(tree);
        return tree;
    }
}
