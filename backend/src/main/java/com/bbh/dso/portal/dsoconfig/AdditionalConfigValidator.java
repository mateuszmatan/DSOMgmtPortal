package com.bbh.dso.portal.dsoconfig;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the free-form YAML of a service against the config.yaml reference of the DevSecOps library:
 * it must be a mapping, use only keys the library knows at the top level and hold no secret, since
 * secrets belong in the Jenkins credentials store.
 */
@Component
public class AdditionalConfigValidator {

    /** Top-level keys of a project entry in config.yaml (documentation chapter 14). */
    static final Set<String> KNOWN_KEYS = Set.of(
            "appId", "buildTool", "deployTarget", "sourceDir", "javaPath", "buildToolAutoSetup", "includedDirs",
            "excludedDirs", "appscanPath", "jenkins", "asoc", "influx", "coverage", "tools", "sast", "sca", "dast",
            "build", "delivery", "scm", "goldenFix", "tests", "deploy", "appName", "artifactName", "baseArtifactName",
            "flutter");

    private static final Set<String> FORBIDDEN_PATHS = Set.of("asoc.keySecret", "influx.token", "dast.loginPassword");

    private static final Set<String> FORBIDDEN_KEYS = Set.of("password", "keySecret", "loginPassword");

    /**
     * Parses the YAML text; an empty text yields an empty map.
     *
     * @throws IllegalArgumentException when the text is not a YAML mapping
     */
    public Map<String, Object> parse(String yamlText) {
        if (yamlText == null || yamlText.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object parsed;
        try {
            parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlText);
        } catch (YAMLException e) {
            throw new IllegalArgumentException("is not valid YAML: " + firstLine(e.getMessage()));
        }
        if (parsed == null) {
            return new LinkedHashMap<>();
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("must be a YAML mapping such as 'tests:' or 'deploy:' sections");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * Returns every problem found, or an empty list when the YAML can be merged into the configuration.
     */
    public List<String> validate(String yamlText) {
        Map<String, Object> config;
        try {
            config = parse(yamlText);
        } catch (IllegalArgumentException e) {
            return List.of(e.getMessage());
        }
        List<String> problems = new ArrayList<>();
        for (String key : config.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                problems.add("'" + key + "' is not a config.yaml key of the DevSecOps library");
            }
        }
        findSecrets("", config, problems);
        return problems;
    }

    private void findSecrets(String prefix, Map<?, ?> map, List<String> problems) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (FORBIDDEN_PATHS.contains(path) || FORBIDDEN_KEYS.contains(key)) {
                problems.add("'" + path + "' is a secret: store it in Jenkins credentials and reference the credential ID");
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                findSecrets(path, nested, problems);
            } else if (entry.getValue() instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nested) {
                        findSecrets(path, nested, problems);
                    }
                }
            }
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
