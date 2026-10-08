package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.REQUIRED;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviateBytes;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record TaskText(String shortDescription, String description) {

    public static final int MAX_TASKS = 50;

    public TaskText {
        shortDescription = trimToNull(shortDescription);
        description = trimToNull(description);
    }

    public static List<TaskText> suggestedTasks(String productName) {
        return List.of(of("Deploy " + productName + " to production", "Deploy the release of " + productName
                        + " in the change window with its deployment jobs, then run the smoke tests of the DevSecOps"
                        + " pipeline and record the result in this task."),
                of("Validate " + productName + " in production", "Run the post-install validation of " + productName
                        + ": the smoke tests and the monitoring, then confirm the release with the business owner in"
                        + " this task."));
    }

    public static void validateTasks(List<TaskText> tasks, ValidationProblems problems) {
        problems.require("tasks", tasks, "add at least one change task");
        if (tasks.size() > MAX_TASKS) {
            problems.add("tasks", "may list at most " + MAX_TASKS + " change tasks");
        }
        for (int index = 0; index < tasks.size(); index++) {
            tasks.get(index).validate(problems.at("tasks[" + index + "]"));
        }
    }

    void validate(ValidationProblems problems) {
        problems.require("shortDescription", shortDescription, REQUIRED)
                .require("description", description, REQUIRED)
                .fits("shortDescription", shortDescription, SHORT_DESCRIPTION_MAX)
                .fits("description", description, DESCRIPTION_MAX);
    }

    private static TaskText of(String shortDescription, String description) {
        return new TaskText(abbreviateBytes(shortDescription, SHORT_DESCRIPTION_MAX),
                abbreviateBytes(description, DESCRIPTION_MAX));
    }
}
