package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.ToolCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.util.List;

@Embeddable
public record ToolCommandEmbeddable(
        @Convert(converter = DelimitedListConverter.Tokens.class)
        @Column(name = "TASKS", length = 1000) List<String> tasks,
        @Convert(converter = DelimitedListConverter.Tokens.class)
        @Column(name = "FLAGS", length = 2000) List<String> flags,
        @Column(name = "DIRECTORY", length = 500) String directory,
        @Column(name = "MAVEN_HOME", length = 500) String mavenHome,
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "ENVIRONMENT", length = 4000) List<String> environment) {

    static ToolCommandEmbeddable of(ToolCommand command) {
        return new ToolCommandEmbeddable(command.tasks(), command.flags(), command.directory(), command.mavenHome(),
                command.environment());
    }

    static ToolCommand toDomain(ToolCommandEmbeddable command) {
        return command == null ? null
                : new ToolCommand(command.tasks, command.flags, command.directory, command.mavenHome, command.environment);
    }
}
