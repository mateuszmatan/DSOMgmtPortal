package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.BuildSettings;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record BuildSettingsEmbeddable(
        @Enumerated(EnumType.STRING)
        @Column(name = "BUILD_TOOL", nullable = false, length = 20) BuildTool tool,
        @Column(name = "SOURCE_DIR", nullable = false, length = 500) String sourceDir,
        @Column(name = "JAVA_PATH", length = 500) String javaPath,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "BUILD_TOOL_AUTO_SETUP", nullable = false) Boolean autoSetup,
        @Column(name = "BUILD_PATH", length = 500) String buildPath,
        @Embedded
        @AttributeOverride(name = "tasks", column = @Column(name = "BUILD_TASKS", length = 1000))
        @AttributeOverride(name = "flags", column = @Column(name = "BUILD_FLAGS", length = 2000))
        @AttributeOverride(name = "directory", column = @Column(name = "BUILD_DIRECTORY", length = 500))
        @AttributeOverride(name = "mavenHome", column = @Column(name = "BUILD_MAVEN_HOME", length = 500))
        @AttributeOverride(name = "environment", column = @Column(name = "BUILD_ENVIRONMENT", length = 4000))
        ToolCommandEmbeddable command) {

    static BuildSettingsEmbeddable of(BuildSettings build) {
        return new BuildSettingsEmbeddable(build.tool(), build.sourceDir(), build.javaPath(), build.autoSetup(),
                build.buildPath(), ToolCommandEmbeddable.of(build.command()));
    }

    BuildSettings toDomain() {
        return new BuildSettings(tool, sourceDir, javaPath, autoSetup, buildPath, ToolCommandEmbeddable.toDomain(command));
    }
}
