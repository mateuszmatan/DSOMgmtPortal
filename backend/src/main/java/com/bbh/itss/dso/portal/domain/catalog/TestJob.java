package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.Map;
import java.util.regex.Pattern;

public record TestJob(TestStage stage, String name, TestJobType type, String job, Integer timeoutMinutes,
                      String parameters, String remoteJenkins, String remoteJenkinsUrl, String credentialsId,
                      Integer pollIntervalSec, String tokenCredentialsId, Boolean abortTriggeredJob,
                      Boolean overrideTrustAllCertificates, Boolean preventRemoteBuildQueue,
                      Boolean trustAllCertificates, Boolean useCrumbCache, Boolean useJobInfoCache) {

    private static final Pattern PARAMETER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]*=.*$");

    public TestJob {
        name = Text.trimToNull(name);
        job = job == null ? null : job.trim();
        parameters = Text.trimToNull(parameters);
        remoteJenkins = Text.trimToNull(remoteJenkins);
        remoteJenkinsUrl = Text.trimToNull(remoteJenkinsUrl);
        credentialsId = Text.trimToNull(credentialsId);
        tokenCredentialsId = Text.trimToNull(tokenCredentialsId);
        abortTriggeredJob = Boolean.TRUE.equals(abortTriggeredJob);
        overrideTrustAllCertificates = Boolean.TRUE.equals(overrideTrustAllCertificates);
        preventRemoteBuildQueue = Boolean.TRUE.equals(preventRemoteBuildQueue);
        trustAllCertificates = Boolean.TRUE.equals(trustAllCertificates);
        useCrumbCache = Boolean.TRUE.equals(useCrumbCache);
        useJobInfoCache = Boolean.TRUE.equals(useJobInfoCache);
    }

    public static TestJob of(TestStage stage, String name, TestJobType type, String job, Integer timeoutMinutes) {
        return new TestJob(stage, name, type, job, timeoutMinutes, null, null, null, null, null, null, false, false,
                false, false, false, false);
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
        return Text.isUrl(job);
    }

    public boolean needsRemoteJenkins() {
        return type == TestJobType.REMOTE && !isUrl() && remoteJenkins == null && remoteJenkinsUrl == null;
    }

    public Map<String, Object> toConfig() {
        return new ConfigTree().set("name", name).set("type", type).set(isUrl() ? "url" : "job", job)
                .set("timeoutMin", timeoutMinutes).set("parameters", parameters).set("remoteJenkins", remoteJenkins)
                .set("remoteJenkinsUrl", remoteJenkinsUrl).set("credentialsId", credentialsId)
                .set("pollIntervalSec", pollIntervalSec).set("tokenCredentialsId", tokenCredentialsId)
                .flag("abortTriggeredJob", abortTriggeredJob)
                .flag("overrideTrustAllCertificates", overrideTrustAllCertificates)
                .flag("preventRemoteBuildQueue", preventRemoteBuildQueue)
                .flag("trustAllCertificates", trustAllCertificates)
                .flag("useCrumbCache", useCrumbCache)
                .flag("useJobInfoCache", useJobInfoCache).toMap();
    }
}
