package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

import java.util.Map;

@Embeddable
public record UrbanCodeComponent(
        @NotBlank @Size(max = 200)
        @Column(name = "COMPONENT_NAME", nullable = false, length = 200)
        String componentName,
        @Size(max = 500)
        @Column(name = "BASE_DIR", length = 500)
        String baseDir,
        @Size(max = 500)
        @Column(name = "FILE_INCLUDE_PATTERNS", length = 500)
        String fileIncludePatterns,
        @Size(max = 500)
        @Column(name = "FILE_EXCLUDE_PATTERNS", length = 500)
        String fileExcludePatterns,
        @Size(max = 200)
        @Column(name = "VERSION_PREFIX", length = 200)
        String versionPrefix,
        @Size(max = 200)
        @Column(name = "COMPONENT_VERSION", length = 200)
        String version,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "INCREMENTAL_VERSION", nullable = false)
        Boolean incrementalVersion) {

    public UrbanCodeComponent {
        componentName = componentName == null ? null : componentName.trim();
        baseDir = Text.trimToNull(baseDir);
        fileIncludePatterns = Text.trimToNull(fileIncludePatterns);
        fileExcludePatterns = Text.trimToNull(fileExcludePatterns);
        versionPrefix = Text.trimToNull(versionPrefix);
        version = Text.trimToNull(version);
        incrementalVersion = !Boolean.FALSE.equals(incrementalVersion);
    }

    public Map<String, Object> toConfig() {
        return new ConfigTree()
                .set("componentName", componentName)
                .set("baseDir", baseDir)
                .set("fileIncludePatterns", fileIncludePatterns)
                .set("fileExcludePatterns", fileExcludePatterns)
                .set("versionPrefix", versionPrefix)
                .set("version", version)
                .set("incrementalVersion", incrementalVersion)
                .toMap();
    }
}
