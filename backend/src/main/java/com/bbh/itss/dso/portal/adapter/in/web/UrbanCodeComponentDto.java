package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UrbanCodeComponentDto(
        @NotBlank @Size(max = 200)
        String componentName,
        @Size(max = 500)
        String baseDir,
        @Size(max = 500)
        String fileIncludePatterns,
        @Size(max = 500)
        String fileExcludePatterns,
        @Size(max = 200)
        String versionPrefix,
        @Size(max = 200)
        String version,
        Boolean incrementalVersion) {

    public UrbanCodeComponentDto {
        componentName = componentName == null ? null : componentName.trim();
        baseDir = Text.trimToNull(baseDir);
        fileIncludePatterns = Text.trimToNull(fileIncludePatterns);
        fileExcludePatterns = Text.trimToNull(fileExcludePatterns);
        versionPrefix = Text.trimToNull(versionPrefix);
        version = Text.trimToNull(version);
        incrementalVersion = !Boolean.FALSE.equals(incrementalVersion);
    }

    static UrbanCodeComponentDto from(UrbanCodeComponent source) {
        return new UrbanCodeComponentDto(source.componentName(), source.baseDir(), source.fileIncludePatterns(),
                source.fileExcludePatterns(), source.versionPrefix(), source.version(), source.incrementalVersion());
    }

    UrbanCodeComponent toDomain() {
        return new UrbanCodeComponent(componentName, baseDir, fileIncludePatterns, fileExcludePatterns, versionPrefix,
                version, incrementalVersion);
    }
}
