package com.bbh.itss.dso.portal.dsoconfig;

import com.bbh.itss.dso.portal.catalog.ConfigTree;
import com.bbh.itss.dso.portal.catalog.Product;
import com.bbh.itss.dso.portal.catalog.ServiceDefinition;
import com.bbh.itss.dso.portal.pipeline.Pipeline;
import com.bbh.itss.dso.portal.pipeline.PipelineSettings;
import com.bbh.itss.dso.portal.pipeline.PipelineType;
import com.bbh.itss.dso.portal.settings.GlobalSettings;
import com.bbh.itss.dso.portal.settings.GlobalSettingsService;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the portal's data in the shape the DevSecOps library reads today, so the library can take its
 * configuration from the portal instead of the config.yaml in the repository and the defaults.yaml in its own
 * resources, without changing how it interprets the values.
 * <p>
 * A service entry is layered: the BBH tool servers of the global settings, then the service's own values, then
 * the global deployment defaults for the deployment sections the service configured.
 */
@Component
public class DsoConfigBuilder {

    /** Top-level key order of the config.yaml reference, so the output reads like the template. */
    static final List<String> KEY_ORDER = List.of(
            "appId", "buildTool", "deployTarget", "sourceDir", "javaPath", "buildToolAutoSetup", "includedDirs",
            "excludedDirs", "appscanPath", "appName", "artifactName", "baseArtifactName", "jenkins", "asoc", "influx",
            "coverage", "tools", "sast", "sca", "dast", "build", "delivery", "scm", "goldenFix", "tests", "deploy",
            "flutter");

    private final GlobalSettingsService settings;

    public DsoConfigBuilder(GlobalSettingsService settings) {
        this.settings = settings;
    }

    /** The configuration of every service of a product, as one config.yaml. */
    public Map<String, Object> productConfig(Product product) {
        GlobalSettings global = settings.current();
        Map<String, Object> projects = new LinkedHashMap<>();
        product.getServices().forEach(service -> projects.put(service.getName(), serviceTree(service, global).toMap(KEY_ORDER)));
        return Map.of("projects", projects);
    }

    /**
     * Everything a pipeline receives for its key: the settings the Jenkinsfile passes today ({@code pipeline}),
     * the BBH tools ({@code platform}), the library defaults ({@code defaults}) and its service ({@code projects}).
     */
    public Map<String, Object> pipelineConfig(Pipeline pipeline) {
        GlobalSettings global = settings.current();
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
        root.put("defaults", global.values().defaultsConfig());
        root.put("projects", Map.of(service.getName(), serviceTree.toMap(KEY_ORDER)));
        return root;
    }

    /** The global part every pipeline shares: the BBH tools and the library defaults. */
    public Map<String, Object> globalConfig() {
        GlobalSettings global = settings.current();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("platform", global.platform().toConfig());
        root.put("defaults", global.values().defaultsConfig());
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

    private static ConfigTree serviceTree(ServiceDefinition service, GlobalSettings global) {
        ConfigTree tree = new ConfigTree();
        global.platform().writeProjectDefaults(tree);
        service.writeTo(tree);
        global.deployment().fillIn(tree);
        return tree;
    }
}
