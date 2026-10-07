package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.StoredList;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ToolCommand(List<String> tasks, List<String> flags, String directory, String mavenHome,
                          List<String> environment, String label, Boolean returnStdout) {

    public static final ToolCommand NONE = new ToolCommand(List.of(), List.of(), null, null, List.of(), null, false);

    public ToolCommand {
        tasks = Text.trimmed(tasks);
        flags = Text.trimmed(flags);
        directory = trimToNull(directory);
        mavenHome = trimToNull(mavenHome);
        environment = Text.trimmed(environment);
        label = trimToNull(label);
        returnStdout = Boolean.TRUE.equals(returnStdout);
    }

    public static ToolCommand of(List<String> tasks, List<String> flags) {
        return new ToolCommand(tasks, flags, null, null, List.of(), null, false);
    }

    public boolean isEmpty() {
        return tasks.isEmpty() && flags.isEmpty() && directory == null && mavenHome == null && environment.isEmpty()
                && label == null && !returnStdout;
    }

    public void writeTo(ConfigTree config, String path, BuildTool tool) {
        if (tool == BuildTool.GRADLE || tool == BuildTool.MAVEN) {
            boolean maven = tool == BuildTool.MAVEN;
            String block = path + (maven ? ".maven." : ".gradle.");
            config.set(block + (maven ? "goals" : "tasks"), tasks)
                    .set(block + "flags", flags)
                    .set(block + "dir", directory)
                    .set(block + "mvnPath", maven ? mavenHome : null)
                    .set(block + "env", environmentMap())
                    .set(block + "label", label)
                    .flag(block + "returnStdout", returnStdout);
        }
    }

    public void validate(ValidationProblems problems) {
        StoredList.LINES_1000.check(problems, "tasks", tasks);
        StoredList.LINES_2000.check(problems, "flags", flags);
        StoredList.LINES_4000.check(problems, "environment", environment);
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
