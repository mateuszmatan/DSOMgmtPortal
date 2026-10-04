package com.bbh.dso.portal.dsoconfig;

import com.bbh.dso.portal.catalog.ConfigTree;
import com.bbh.dso.portal.catalog.Product;
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
 * Renders portal data in the shape of the config.yaml the DevSecOps library reads today, so the library can
 * switch from the file in the repository to the portal without changing how it interprets the values.
 * <p>
 * A service entry is layered: the BBH-wide defaults, then the service's additional YAML, then the values
 * with a dedicated field in the portal, which always win.
 */
@Component
public class DsoConfigBuilder {

    /** Top-level key order of the config.yaml reference, so the output reads like the template. */
    static final List<String> KEY_ORDER = List.of(
            "appId", "buildTool", "deployTarget", "sourceDir", "javaPath", "buildToolAutoSetup", "includedDirs",
            "excludedDirs", "appName", "artifactName", "baseArtifactName", "jenkins", "asoc", "influx", "coverage",
            "tools", "sast", "sca", "dast", "build", "delivery", "scm", "goldenFix", "tests", "deploy", "flutter");

    private final DsoDefaultsProperties defaults;

    public DsoConfigBuilder(DsoDefaultsProperties defaults) {
        this.defaults = defaults;
    }

    /** The configuration of every service of a product, as one config.yaml. */
    public Map<String, Object> productConfig(Product product) {
        Map<String, Object> projects = new LinkedHashMap<>();
        product.getServices().forEach(service -> projects.put(service.getName(), serviceTree(service).toMap(KEY_ORDER)));
        return Map.of("projects", projects);
    }

    /**
     * What a pipeline receives for its key: the settings that live in the Jenkinsfile today, plus the
     * {@code projects:} section with the pipeline's service.
     */
    public Map<String, Object> pipelineConfig(Pipeline pipeline) {
        ServiceDefinition service = pipeline.getService();
        ConfigTree serviceTree = serviceTree(service);
        if (pipeline.getType() == PipelineType.SECURITY) {
            serviceTree.set("jenkins.pipeline.extendedPipeline", pipeline.getSettings().extendedPipelineJob());
        }

        Map<String, Object> pipelineSection = new LinkedHashMap<>();
        pipelineSection.put("type", pipeline.getType().variant());
        pipelineSection.put("entryPoint", pipeline.getType().entryPoint());
        pipelineSection.put("product", service.getProduct().getCode());
        pipelineSection.put("projectNames", service.getName());
        pipelineSection.put("agentNames", pipeline.getSettings().agentLabels());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("pipeline", pipelineSection);
        root.put("projects", Map.of(service.getName(), serviceTree.toMap(KEY_ORDER)));
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

    private ConfigTree serviceTree(ServiceDefinition service) {
        ConfigTree tree = new ConfigTree();
        defaults.writeTo(tree);
        service.writeTo(tree);
        return tree;
    }
}
