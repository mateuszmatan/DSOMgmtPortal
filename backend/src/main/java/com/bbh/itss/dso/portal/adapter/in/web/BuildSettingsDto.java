package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.BuildSettings;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BuildSettingsDto(
        @NotNull
        BuildTool tool,
        @Size(max = 500)
        String sourceDir,
        @Size(max = 500)
        String javaPath,
        Boolean autoSetup,
        @Size(max = 500)
        String buildPath,
        @Valid
        ToolCommandDto command) {

    public BuildSettingsDto {
        sourceDir = Text.orDefault(sourceDir, BuildSettings.DEFAULT_SOURCE_DIR);
        javaPath = Text.trimToNull(javaPath);
        autoSetup = Boolean.TRUE.equals(autoSetup);
        buildPath = Text.trimToNull(buildPath);
        command = command == null ? ToolCommandDto.NONE : command;
    }

    static BuildSettingsDto from(BuildSettings source) {
        return new BuildSettingsDto(source.tool(), source.sourceDir(), source.javaPath(), source.autoSetup(),
                source.buildPath(), ToolCommandDto.from(source.command()));
    }

    BuildSettings toDomain() {
        return new BuildSettings(tool, sourceDir, javaPath, autoSetup, buildPath, command.toDomain());
    }
}
