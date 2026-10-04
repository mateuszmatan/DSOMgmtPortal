package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.DelimitedListConverter;
import com.bbh.itss.dso.portal.common.Text;
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

/**
 * A Gradle or Maven command the library runs with its {@code BuildRunner}: the tasks (Gradle) or goals (Maven),
 * extra flags, the directory to run in, the Maven installation and environment variables. The same shape is
 * used by {@code build}, {@code tests.unitTests}, {@code tools.sonar}, {@code asoc} and {@code delivery}; the
 * columns are named where the command is embedded.
 */
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
        tasks = DelimitedListConverter.trimmed(tasks);
        flags = DelimitedListConverter.trimmed(flags);
        directory = Text.trimToNull(directory);
        mavenHome = Text.trimToNull(mavenHome);
        environment = DelimitedListConverter.trimmed(environment);
    }

    /** A command made of tasks or goals only. */
    public static ToolCommand of(List<String> tasks, List<String> flags) {
        return new ToolCommand(tasks, flags, null, null, List.of());
    }

    @JsonIgnore
    public boolean isEmpty() {
        return tasks.isEmpty() && flags.isEmpty() && directory == null && mavenHome == null && environment.isEmpty();
    }

    /**
     * Writes the command under {@code <path>.gradle} or {@code <path>.maven}, the key the library picks by the
     * build tool. A Flutter build runs no Gradle or Maven command, so nothing is written for it.
     */
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

    /** The variables as the library's {@code env} map; a value keeps any further '=' it contains. */
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
