package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.ValidationProblems;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.Size;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Free-form YAML for the config.yaml keys without a dedicated field in the portal, such as {@code tests},
 * {@code deploy} or {@code build}. It must be a mapping, may only use keys the library knows at the top level
 * and must hold no secret, since secrets belong in the Jenkins credentials store.
 */
@Embeddable
public record AdditionalConfig(
        @Size(max = 100_000)
        @Lob
        @Column(name = "ADDITIONAL_CONFIG", length = Integer.MAX_VALUE)
        String yaml) implements ConfigSection {

    public static final AdditionalConfig NONE = new AdditionalConfig(null);

    /** Top-level keys of a project entry in config.yaml (DevSecOps documentation, chapter 14). */
    static final Set<String> KNOWN_KEYS = Set.of(
            "appId", "buildTool", "deployTarget", "sourceDir", "javaPath", "buildToolAutoSetup", "includedDirs",
            "excludedDirs", "appscanPath", "jenkins", "asoc", "influx", "coverage", "tools", "sast", "sca", "dast",
            "build", "delivery", "scm", "goldenFix", "tests", "deploy", "appName", "artifactName", "baseArtifactName",
            "flutter");

    private static final Set<String> SECRET_PATHS = Set.of("asoc.keySecret", "influx.token", "dast.loginPassword");
    private static final Set<String> SECRET_KEYS = Set.of("password", "keySecret", "loginPassword");

    public AdditionalConfig {
        yaml = yaml == null || yaml.isBlank() ? null : yaml.strip();
    }

    /**
     * The YAML as a map; empty when there is none.
     *
     * @throws IllegalArgumentException when the text is not a YAML mapping
     */
    public Map<String, Object> parse() {
        if (yaml == null) {
            return new LinkedHashMap<>();
        }
        Object parsed;
        try {
            parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
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

    @Override
    public void writeTo(ConfigTree config) {
        config.merge(parse());
    }

    @Override
    public void validate(ValidationProblems problems) {
        Map<String, Object> config;
        try {
            config = parse();
        } catch (IllegalArgumentException e) {
            problems.add("yaml", e.getMessage());
            return;
        }
        config.keySet().stream()
                .filter(key -> !KNOWN_KEYS.contains(key))
                .forEach(key -> problems.add("yaml", "'" + key + "' is not a config.yaml key of the DevSecOps library"));
        findSecrets("", config, problems);
    }

    private static void findSecrets(String prefix, Map<?, ?> map, ValidationProblems problems) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (SECRET_PATHS.contains(path) || SECRET_KEYS.contains(key)) {
                problems.add("yaml", "'" + path + "' is a secret: store it in Jenkins credentials and reference the credential ID");
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                findSecrets(path, nested, problems);
            } else if (entry.getValue() instanceof List<?> list) {
                list.stream().filter(Map.class::isInstance).forEach(item -> findSecrets(path, (Map<?, ?>) item, problems));
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
