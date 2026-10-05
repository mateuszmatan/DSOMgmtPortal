package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.LinkedHashMap;
import java.util.Map;

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

    public TestJob {
        name = Text.trimToNull(name);
        job = job == null ? null : job.trim();
        parameters = Text.trimToNull(parameters);
        remoteJenkins = Text.trimToNull(remoteJenkins);
        remoteJenkinsUrl = Text.trimToNull(remoteJenkinsUrl);
        credentialsId = Text.trimToNull(credentialsId);
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
