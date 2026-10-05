package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.TestJob;
import com.bbh.itss.dso.portal.domain.catalog.TestJobType;
import com.bbh.itss.dso.portal.domain.catalog.TestStage;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL_MESSAGE;

public record TestJobDto(
        @NotNull
        TestStage stage,
        @Size(max = 200)
        String name,
        TestJobType type,
        @NotBlank
        @Size(max = 1000)
        String job,
        @Min(1) @Max(1440)
        Integer timeoutMinutes,
        @Size(max = 2000)
        String parameters,
        @Size(max = 200)
        String remoteJenkins,
        @Size(max = 1000)
        @Pattern(regexp = URL, message = URL_MESSAGE)
        String remoteJenkinsUrl,
        @Size(max = 200)
        String credentialsId) {

    public TestJobDto {
        name = Text.trimToNull(name);
        job = job == null ? null : job.trim();
        parameters = Text.trimToNull(parameters);
        remoteJenkins = Text.trimToNull(remoteJenkins);
        remoteJenkinsUrl = Text.trimToNull(remoteJenkinsUrl);
        credentialsId = Text.trimToNull(credentialsId);
    }

    static TestJobDto from(TestJob source) {
        return new TestJobDto(source.stage(), source.name(), source.type(), source.job(), source.timeoutMinutes(),
                source.parameters(), source.remoteJenkins(), source.remoteJenkinsUrl(), source.credentialsId());
    }

    TestJob toDomain() {
        return new TestJob(stage, name, type, job, timeoutMinutes, parameters, remoteJenkins, remoteJenkinsUrl,
                credentialsId);
    }
}
