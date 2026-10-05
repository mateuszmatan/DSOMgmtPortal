package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.TestJob;
import com.bbh.itss.dso.portal.domain.catalog.TestJobType;
import com.bbh.itss.dso.portal.domain.catalog.TestStage;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record TestJobEmbeddable(
        @Enumerated(EnumType.STRING)
        @Column(name = "STAGE", nullable = false, length = 20) TestStage stage,
        @Column(name = "NAME", length = 200) String name,
        @Enumerated(EnumType.STRING)
        @Column(name = "JOB_TYPE", length = 20) TestJobType type,
        @Column(name = "JOB", nullable = false, length = 1000) String job,
        @Column(name = "TIMEOUT_MINUTES") Integer timeoutMinutes,
        @Column(name = "PARAMETERS", length = 2000) String parameters,
        @Column(name = "REMOTE_JENKINS", length = 200) String remoteJenkins,
        @Column(name = "REMOTE_JENKINS_URL", length = 1000) String remoteJenkinsUrl,
        @Column(name = "CREDENTIALS_ID", length = 200) String credentialsId) {

    static TestJobEmbeddable of(TestJob job) {
        return new TestJobEmbeddable(job.stage(), job.name(), job.type(), job.job(), job.timeoutMinutes(),
                job.parameters(), job.remoteJenkins(), job.remoteJenkinsUrl(), job.credentialsId());
    }

    TestJob toDomain() {
        return new TestJob(stage, name, type, job, timeoutMinutes, parameters, remoteJenkins, remoteJenkinsUrl,
                credentialsId);
    }
}
