package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import com.bbh.itss.dso.portal.common.ValidationProblems;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

/**
 * How the service is built: {@code buildTool}, {@code sourceDir}, {@code javaPath}, {@code buildToolAutoSetup},
 * the artifact path {@code build.buildPath} and the Gradle or Maven command of the build stage
 * ({@code build.gradle} or {@code build.maven}).
 */
@Embeddable
public record BuildSettings(
        @NotNull
        @Enumerated(EnumType.STRING)
        @Column(name = "BUILD_TOOL", nullable = false, length = 20)
        BuildTool tool,
        @Size(max = 500)
        @Column(name = "SOURCE_DIR", nullable = false, length = 500)
        String sourceDir,
        @Size(max = 500)
        @Column(name = "JAVA_PATH", length = 500)
        String javaPath,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "BUILD_TOOL_AUTO_SETUP", nullable = false)
        Boolean autoSetup,
        @Size(max = 500)
        @Column(name = "BUILD_PATH", length = 500)
        String buildPath,
        @Valid
        @Embedded
        @AttributeOverride(name = "tasks", column = @Column(name = "BUILD_TASKS", length = 1000))
        @AttributeOverride(name = "flags", column = @Column(name = "BUILD_FLAGS", length = 2000))
        @AttributeOverride(name = "directory", column = @Column(name = "BUILD_DIRECTORY", length = 500))
        @AttributeOverride(name = "mavenHome", column = @Column(name = "BUILD_MAVEN_HOME", length = 500))
        @AttributeOverride(name = "environment", column = @Column(name = "BUILD_ENVIRONMENT", length = 4000))
        ToolCommand command) implements ConfigSection {

    public static final String DEFAULT_SOURCE_DIR = ".";

    public BuildSettings {
        sourceDir = Text.orDefault(sourceDir, DEFAULT_SOURCE_DIR);
        javaPath = Text.trimToNull(javaPath);
        autoSetup = Boolean.TRUE.equals(autoSetup);
        buildPath = Text.trimToNull(buildPath);
        command = command == null ? ToolCommand.NONE : command;
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("buildTool", tool.configValue()).set("sourceDir", sourceDir).set("javaPath", javaPath);
        if (autoSetup) {
            config.set("buildToolAutoSetup", true);
        }
        config.set("build.buildPath", buildPath);
        command.writeTo(config, "build", tool);
    }

    @Override
    public void validate(ValidationProblems problems) {
        // The unit tests stage stops with "JAVA_HOME parameter is not specified" without a JDK for Gradle and Maven.
        if (tool != BuildTool.FLUTTER && javaPath == null && !autoSetup) {
            problems.add("javaPath", "set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them");
        }
        // The build stage stops with "tasks must be provided" without a Gradle or Maven command.
        if (tool != BuildTool.FLUTTER && command.tasks().isEmpty()) {
            problems.add("command.tasks", tool == BuildTool.MAVEN
                    ? "add the Maven goals of the build, for example clean verify"
                    : "add the Gradle tasks of the build, for example clean build");
        }
    }
}
