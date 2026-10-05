package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ToolCommand(
        List<String> tasks,
        List<String> flags,
        String directory,
        String mavenHome,
        List<String> environment) {

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
