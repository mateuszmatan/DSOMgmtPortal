package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import com.bbh.itss.dso.portal.common.ValidationProblems;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

/**
 * How the service is built and tested: {@code buildTool}, {@code sourceDir}, {@code javaPath} and
 * {@code buildToolAutoSetup}.
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
        Boolean autoSetup) implements ConfigSection {

    public static final String DEFAULT_SOURCE_DIR = ".";

    public BuildSettings {
        sourceDir = Text.orDefault(sourceDir, DEFAULT_SOURCE_DIR);
        javaPath = Text.trimToNull(javaPath);
        autoSetup = Boolean.TRUE.equals(autoSetup);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("buildTool", tool.configValue()).set("sourceDir", sourceDir).set("javaPath", javaPath);
        if (autoSetup) {
            config.set("buildToolAutoSetup", true);
        }
    }

    /** The unit tests stage stops with "JAVA_HOME parameter is not specified" without a JDK for Gradle and Maven. */
    @Override
    public void validate(ValidationProblems problems) {
        if (tool != BuildTool.FLUTTER && javaPath == null && !autoSetup) {
            problems.add("javaPath", "set the JDK path or enable automatic build tool setup, the unit tests stage needs one of them");
        }
    }
}
