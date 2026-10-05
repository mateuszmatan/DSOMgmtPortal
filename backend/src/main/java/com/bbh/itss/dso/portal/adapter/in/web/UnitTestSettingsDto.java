package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.UnitTestSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UnitTestSettingsDto(
        @Valid
        ToolCommandDto command,
        @Size(max = 500)
        String resultPattern,
        @Size(max = 500)
        String rootDir,
        @Size(max = 500)
        String reportOutDir,
        Boolean allowEmptyResults,
        @Size(max = 500)
        String coverageReportPath) {

    public UnitTestSettingsDto {
        command = command == null ? ToolCommandDto.NONE : command;
        resultPattern = Text.trimToNull(resultPattern);
        rootDir = Text.trimToNull(rootDir);
        reportOutDir = Text.trimToNull(reportOutDir);
        allowEmptyResults = Boolean.TRUE.equals(allowEmptyResults);
        coverageReportPath = Text.trimToNull(coverageReportPath);
    }

    static UnitTestSettingsDto from(UnitTestSettings source) {
        return new UnitTestSettingsDto(ToolCommandDto.from(source.command()), source.resultPattern(), source.rootDir(),
                source.reportOutDir(), source.allowEmptyResults(), source.coverageReportPath());
    }

    UnitTestSettings toDomain() {
        return new UnitTestSettings(command.toDomain(), resultPattern, rootDir, reportOutDir, allowEmptyResults,
                coverageReportPath);
    }
}
