package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.ToolCommand;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ToolCommandDto(
        @Size(max = 30)
        List<@NotBlank @Size(max = 200) String> tasks,
        @Size(max = 40)
        List<@NotBlank @Size(max = 300) String> flags,
        @Size(max = 500)
        String directory,
        @Size(max = 500)
        String mavenHome,
        @Size(max = 30)
        List<@Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_]*=.*$", message = "write each variable as NAME=value")
                @Size(max = 500) String> environment) {

    static final ToolCommandDto NONE = new ToolCommandDto(List.of(), List.of(), null, null, List.of());

    public ToolCommandDto {
        tasks = Text.trimmed(tasks);
        flags = Text.trimmed(flags);
        directory = Text.trimToNull(directory);
        mavenHome = Text.trimToNull(mavenHome);
        environment = Text.trimmed(environment);
    }

    static ToolCommandDto from(ToolCommand source) {
        return new ToolCommandDto(source.tasks(), source.flags(), source.directory(), source.mavenHome(),
                source.environment());
    }

    ToolCommand toDomain() {
        return new ToolCommand(tasks, flags, directory, mavenHome, environment);
    }
}
