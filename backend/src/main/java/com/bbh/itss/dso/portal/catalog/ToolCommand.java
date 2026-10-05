package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.adapter.out.persistence.DelimitedListConverter;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Embeddable
public record ToolCommand(
        @Size(max = 30)
        @Convert(converter = DelimitedListConverter.Tokens.class)
        @Column(name = "TASKS", length = 1000)
        List<@NotBlank @Size(max = 200) String> tasks,
        @Size(max = 40)
        @Convert(converter = DelimitedListConverter.Tokens.class)
        @Column(name = "FLAGS", length = 2000)
        List<@NotBlank @Size(max = 300) String> flags,
        @Size(max = 500)
        @Column(name = "DIRECTORY", length = 500)
        String directory,
        @Size(max = 500)
        @Column(name = "MAVEN_HOME", length = 500)
        String mavenHome,
        @Size(max = 30)
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "ENVIRONMENT", length = 4000)
        List<@Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_]*=.*$", message = "write each variable as NAME=value")
                @Size(max = 500) String> environment) {

    public static final ToolCommand NONE = new ToolCommand(List.of(), List.of(), null, null, List.of());

    public ToolCommand {
        tasks = Text.trimmed(tasks);
        flags = Text.trimmed(flags);
        directory = Text.trimToNull(directory);
        mavenHome = Text.trimToNull(mavenHome);
        environment = Text.trimmed(environment);
    }

    public static ToolCommand of(List<String> tasks, List<String> flags) {
        return new ToolCommand(tasks, flags, null, null, List.of());
    }

    @JsonIgnore
    public boolean isEmpty() {
        return tasks.isEmpty() && flags.isEmpty() && directory == null && mavenHome == null && environment.isEmpty();
    }

    public void writeTo(ConfigTree config, String path, BuildTool tool) {
        if (tool == BuildTool.GRADLE) {
            config.set(path + ".gradle.tasks", tasks)
                    .set(path + ".gradle.flags", flags)
                    .set(path + ".gradle.dir", directory)
                    .set(path + ".gradle.env", environmentMap());
        } else if (tool == BuildTool.MAVEN) {
            config.set(path + ".maven.goals", tasks)
                    .set(path + ".maven.flags", flags)
                    .set(path + ".maven.dir", directory)
                    .set(path + ".maven.mvnPath", mavenHome)
                    .set(path + ".maven.env", environmentMap());
        }
    }

    public Map<String, String> environmentMap() {
        Map<String, String> variables = new LinkedHashMap<>();
        for (String variable : environment) {
            int separator = variable.indexOf('=');
            if (separator > 0) {
                variables.put(variable.substring(0, separator).trim(), variable.substring(separator + 1).trim());
            }
        }
        return variables;
    }
}
