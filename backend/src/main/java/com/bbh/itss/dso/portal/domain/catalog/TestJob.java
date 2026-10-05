package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public record TestJob(
        TestStage stage,
        String name,
        TestJobType type,
        String job,
        Integer timeoutMinutes,
        String parameters,
        String remoteJenkins,
        String remoteJenkinsUrl,
        String credentialsId) {

    private static final Pattern PARAMETER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]*=.*$");

    public TestJob {
        name = Text.trimToNull(name);
        job = job == null ? null : job.trim();
        parameters = Text.trimToNull(parameters);
        remoteJenkins = Text.trimToNull(remoteJenkins);
        remoteJenkinsUrl = Text.trimToNull(remoteJenkinsUrl);
        credentialsId = Text.trimToNull(credentialsId);
    }

    public void validate(ValidationProblems problems) {
        if (needsRemoteJenkins()) {
            problems.add("remoteJenkins", "name the remote Jenkins or its URL, or give the job as a full URL");
        }
        if (parameters != null && parameters.lines().anyMatch(line -> !line.isBlank() && !PARAMETER.matcher(line).matches())) {
            problems.add("parameters", "write one parameter per line as NAME=value");
        }
    }

    public boolean isUrl() {
        return job != null && (job.startsWith("http://") || job.startsWith("https://"));
    }

    public boolean needsRemoteJenkins() {
        return type == TestJobType.REMOTE && !isUrl() && remoteJenkins == null && remoteJenkinsUrl == null;
    }

    public Map<String, Object> toConfig() {
        Map<String, Object> entry = new LinkedHashMap<>();
        put(entry, "name", name);
        put(entry, "type", type == null ? null : type.configValue());
        entry.put(isUrl() ? "url" : "job", job);
        put(entry, "timeoutMin", timeoutMinutes);
        put(entry, "parameters", parameters);
        put(entry, "remoteJenkins", remoteJenkins);
        put(entry, "remoteJenkinsUrl", remoteJenkinsUrl);
        put(entry, "credentialsId", credentialsId);
        return entry;
    }

    private static void put(Map<String, Object> entry, String key, Object value) {
        if (value != null) {
            entry.put(key, value);
        }
    }
}
