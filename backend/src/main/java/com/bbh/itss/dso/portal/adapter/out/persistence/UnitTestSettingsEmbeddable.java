package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record UnitTestSettingsEmbeddable(
        @Embedded
        @AttributeOverride(name = "tasks", column = @Column(name = "UNIT_TEST_TASKS", length = 1000))
        @AttributeOverride(name = "flags", column = @Column(name = "UNIT_TEST_FLAGS", length = 2000))
        @AttributeOverride(name = "directory", column = @Column(name = "UNIT_TEST_DIRECTORY", length = 500))
        @AttributeOverride(name = "mavenHome", column = @Column(name = "UNIT_TEST_MAVEN_HOME", length = 500))
        @AttributeOverride(name = "environment", column = @Column(name = "UNIT_TEST_ENVIRONMENT", length = 4000))
        ToolCommandEmbeddable command,
        @Column(name = "UNIT_TEST_RESULTS", length = 500) String resultPattern,
        @Column(name = "UNIT_TEST_ROOT_DIR", length = 500) String rootDir,
        @Column(name = "UNIT_TEST_REPORT_DIR", length = 500) String reportOutDir,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UNIT_TEST_ALLOW_EMPTY", nullable = false) Boolean allowEmptyResults,
        @Column(name = "COVERAGE_REPORT_PATH", length = 500) String coverageReportPath) {

    static UnitTestSettingsEmbeddable of(UnitTestSettings unitTests) {
        return new UnitTestSettingsEmbeddable(ToolCommandEmbeddable.of(unitTests.command()), unitTests.resultPattern(),
                unitTests.rootDir(), unitTests.reportOutDir(), unitTests.allowEmptyResults(),
                unitTests.coverageReportPath());
    }

    static UnitTestSettings toDomain(UnitTestSettingsEmbeddable unitTests) {
        return unitTests == null ? null : new UnitTestSettings(ToolCommandEmbeddable.toDomain(unitTests.command),
                unitTests.resultPattern, unitTests.rootDir, unitTests.reportOutDir, unitTests.allowEmptyResults,
                unitTests.coverageReportPath);
    }
}
