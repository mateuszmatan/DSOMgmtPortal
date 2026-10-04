package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import com.bbh.itss.dso.portal.common.ValidationProblems;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record UnitTestSettings(
        @Valid
        @Embedded
        @AttributeOverride(name = "tasks", column = @Column(name = "UNIT_TEST_TASKS", length = 1000))
        @AttributeOverride(name = "flags", column = @Column(name = "UNIT_TEST_FLAGS", length = 2000))
        @AttributeOverride(name = "directory", column = @Column(name = "UNIT_TEST_DIRECTORY", length = 500))
        @AttributeOverride(name = "mavenHome", column = @Column(name = "UNIT_TEST_MAVEN_HOME", length = 500))
        @AttributeOverride(name = "environment", column = @Column(name = "UNIT_TEST_ENVIRONMENT", length = 4000))
        ToolCommand command,
        @Size(max = 500)
        @Column(name = "UNIT_TEST_RESULTS", length = 500)
        String resultPattern,
        @Size(max = 500)
        @Column(name = "UNIT_TEST_ROOT_DIR", length = 500)
        String rootDir,
        @Size(max = 500)
        @Column(name = "UNIT_TEST_REPORT_DIR", length = 500)
        String reportOutDir,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UNIT_TEST_ALLOW_EMPTY", nullable = false)
        Boolean allowEmptyResults,
        @Size(max = 500)
        @Column(name = "COVERAGE_REPORT_PATH", length = 500)
        String coverageReportPath) {

    public static final UnitTestSettings NONE = new UnitTestSettings(null, null, null, null, false, null);

    public UnitTestSettings {
        command = command == null ? ToolCommand.NONE : command;
        resultPattern = Text.trimToNull(resultPattern);
        rootDir = Text.trimToNull(rootDir);
        reportOutDir = Text.trimToNull(reportOutDir);
        allowEmptyResults = Boolean.TRUE.equals(allowEmptyResults);
        coverageReportPath = Text.trimToNull(coverageReportPath);
    }

    public void writeTo(ConfigTree config, BuildTool tool) {
        command.writeTo(config, "tests.unitTests", tool);
        config.set("tests.unitTests.unitTestResult", resultPattern)
                .set("tests.unitTests.rootDir", rootDir)
                .set("tests.unitTests.reportOutDir", reportOutDir);
        if (allowEmptyResults) {
            config.set("tests.unitTests.allowEmptyResults", true);
        }
        config.set("coverage.reportPath", coverageReportPath);
    }

    public void validate(ValidationProblems problems, BuildTool tool) {
        boolean configured = !command.isEmpty() || resultPattern != null || rootDir != null || reportOutDir != null
                || allowEmptyResults;
        if (tool != BuildTool.FLUTTER && configured && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == BuildTool.MAVEN
                    ? "add the Maven goals of the unit tests, for example test jacoco:report"
                    : "add the Gradle tasks of the unit tests, for example test jacocoTestReport");
        }
    }
}
