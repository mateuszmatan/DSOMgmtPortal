package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record UrbanCodeComponentEmbeddable(
        @Column(name = "COMPONENT_NAME", nullable = false, length = 200) String componentName,
        @Column(name = "BASE_DIR", length = 500) String baseDir,
        @Column(name = "FILE_INCLUDE_PATTERNS", length = 500) String fileIncludePatterns,
        @Column(name = "FILE_EXCLUDE_PATTERNS", length = 500) String fileExcludePatterns,
        @Column(name = "VERSION_PREFIX", length = 200) String versionPrefix,
        @Column(name = "COMPONENT_VERSION", length = 200) String version,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "INCREMENTAL_VERSION", nullable = false) Boolean incrementalVersion) {

    static UrbanCodeComponentEmbeddable of(UrbanCodeComponent component) {
        return new UrbanCodeComponentEmbeddable(component.componentName(), component.baseDir(),
                component.fileIncludePatterns(), component.fileExcludePatterns(), component.versionPrefix(),
                component.version(), component.incrementalVersion());
    }

    UrbanCodeComponent toDomain() {
        return new UrbanCodeComponent(componentName, baseDir, fileIncludePatterns, fileExcludePatterns, versionPrefix,
                version, incrementalVersion);
    }
}
