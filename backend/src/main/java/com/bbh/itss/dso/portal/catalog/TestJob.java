package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

@Embeddable
public record TestJob(
        @NotNull
        @Enumerated(EnumType.STRING)
        @Column(name = "STAGE", nullable = false, length = 20)
        TestStage stage,
        @Size(max = 200)
        @Column(name = "NAME", length = 200)
        String name,
        @Enumerated(EnumType.STRING)
        @Column(name = "JOB_TYPE", length = 20)
        TestJobType type,
        @NotBlank
        @Size(max = 1000)
        @Column(name = "JOB", nullable = false, length = 1000)
        String job,
        @Min(1) @Max(1440)
        @Column(name = "TIMEOUT_MINUTES")
        Integer timeoutMinutes,
        @Size(max = 2000)
        @Column(name = "PARAMETERS", length = 2000)
        String parameters,
        @Size(max = 200)
        @Column(name = "REMOTE_JENKINS", length = 200)
        String remoteJenkins,
        @Size(max = 1000)
        @Pattern(regexp = "^(https?://\\S+)?$", message = "must be an http or https URL")
        @Column(name = "REMOTE_JENKINS_URL", length = 1000)
        String remoteJenkinsUrl,
        @Size(max = 200)
        @Column(name = "CREDENTIALS_ID", length = 200)
        String credentialsId) {

    public TestJob {
        name = Text.trimToNull(name);
        job = job == null ? null : job.trim();
        parameters = Text.trimToNull(parameters);
        remoteJenkins = Text.trimToNull(remoteJenkins);
        remoteJenkinsUrl = Text.trimToNull(remoteJenkinsUrl);
        credentialsId = Text.trimToNull(credentialsId);
    }

    @JsonIgnore
    public boolean isUrl() {
        return job != null && (job.startsWith("http://") || job.startsWith("https://"));
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
