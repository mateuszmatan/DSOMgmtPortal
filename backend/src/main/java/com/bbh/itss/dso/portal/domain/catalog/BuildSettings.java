package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

public record BuildSettings(BuildTool tool, String sourceDir, String javaPath, Boolean autoSetup, String buildPath,
                            ToolCommand command) {

    public static final String DEFAULT_SOURCE_DIR = ".";

    public BuildSettings {
        sourceDir = Text.orDefault(sourceDir, DEFAULT_SOURCE_DIR);
        javaPath = Text.trimToNull(javaPath);
        autoSetup = Boolean.TRUE.equals(autoSetup);
        buildPath = Text.trimToNull(buildPath);
        command = command == null ? ToolCommand.NONE : command;
    }

    public void writeTo(ConfigTree config) {
        config.set("buildTool", tool).set("sourceDir", sourceDir).set("javaPath", javaPath)
                .flag("buildToolAutoSetup", autoSetup).set("build.buildPath", buildPath);
        command.writeTo(config, "build", tool);
    }

    public void validate(ValidationProblems problems) {
        if (tool == BuildTool.FLUTTER) {
            validateFlutter(problems);
        } else if (javaPath == null && !autoSetup) {
            problems.add("javaPath", "set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them");
        }
        if (tool != BuildTool.FLUTTER && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == BuildTool.MAVEN
                    ? "add the Maven goals of the build, for example clean verify"
                    : "add the Gradle tasks of the build, for example clean build");
        }
        command.validate(problems.at("command"));
    }

    private void validateFlutter(ValidationProblems problems) {
        problems.require("javaPath", javaPath,
                "is required for Flutter: the build and unit test stages set JAVA_HOME from it");
        if (autoSetup) {
            problems.add("autoSetup", "must be off for Flutter: the automatic build tool setup cannot prepare a Flutter build");
        }
    }
}
