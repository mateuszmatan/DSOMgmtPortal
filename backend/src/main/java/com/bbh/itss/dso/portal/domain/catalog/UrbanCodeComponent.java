package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.Map;

public record UrbanCodeComponent(String componentName, String baseDir, String fileIncludePatterns,
                                 String fileExcludePatterns, String versionPrefix, String version,
                                 Boolean incrementalVersion) {

    public UrbanCodeComponent {
        componentName = componentName == null ? null : componentName.trim();
        baseDir = Text.trimToNull(baseDir);
        fileIncludePatterns = Text.trimToNull(fileIncludePatterns);
        fileExcludePatterns = Text.trimToNull(fileExcludePatterns);
        versionPrefix = Text.trimToNull(versionPrefix);
        version = Text.trimToNull(version);
        incrementalVersion = !Boolean.FALSE.equals(incrementalVersion);
    }

    public void validate(ValidationProblems problems) {
        problems.require("baseDir", baseDir,
                "is required: the deployment uploads the component's files from this folder");
        problems.require("fileIncludePatterns", fileIncludePatterns,
                "is required: the deployment uploads the files matching these patterns");
    }

    public Map<String, Object> toConfig() {
        return new ConfigTree()
                .set("componentName", componentName)
                .set("baseDir", baseDir)
                .set("fileIncludePatterns", fileIncludePatterns)
                .set("fileExcludePatterns", fileExcludePatterns)
                .set("versionPrefix", versionPrefix)
                .set("version", version)
                .set("incrementalVersion", incrementalVersion)
                .toMap();
    }
}
