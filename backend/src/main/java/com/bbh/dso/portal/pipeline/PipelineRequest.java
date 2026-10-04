package com.bbh.dso.portal.pipeline;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A pipeline to add to a service, or the new settings of an existing one. The type cannot change.
 */
public record PipelineRequest(
        @NotNull PipelineType type,
        @NotEmpty(message = "add at least one Jenkins agent label") @Size(max = 20)
        List<@Pattern(regexp = "^[A-Za-z0-9._-]{1,100}$", message = "agent labels may contain letters, digits, '.', '-' and '_'")
                String> agentLabels,
        @Size(max = 500) String extendedPipelineJob,
        @Size(max = 1000) String description) {

    PipelineSettings settings() {
        return new PipelineSettings(agentLabels, extendedPipelineJob, description);
    }
}
