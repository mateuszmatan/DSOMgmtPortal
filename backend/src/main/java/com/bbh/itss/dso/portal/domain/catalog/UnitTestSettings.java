package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

public record UnitTestSettings(ToolCommand command, String resultPattern, String rootDir, String reportOutDir,
                               Boolean allowEmptyResults, String coverageReportPath) {

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
                .set("tests.unitTests.reportOutDir", reportOutDir)
                .flag("tests.unitTests.allowEmptyResults", allowEmptyResults)
                .set("coverage.reportPath", coverageReportPath);
    }

    public void validate(ValidationProblems problems, BuildTool tool) {
        boolean configured = !command.isEmpty() || resultPattern != null || rootDir != null || reportOutDir != null
                || allowEmptyResults;
        if (tool != BuildTool.FLUTTER && configured && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == BuildTool.MAVEN
                    ? "add the Maven goals of the unit tests, for example test jacoco:report"
                    : "add the Gradle tasks of the unit tests, for example test jacocoTestReport");
        }
        command.validate(problems.at("command"));
    }
}
