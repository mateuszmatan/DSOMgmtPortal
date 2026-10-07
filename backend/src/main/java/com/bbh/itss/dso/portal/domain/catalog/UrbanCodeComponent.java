package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record UrbanCodeComponent(String componentName, String baseDir, String fileIncludePatterns,
                                 String fileExcludePatterns, String versionPrefix, String version,
                                 Boolean incrementalVersion, String extensions, String charset,
                                 String pushDescription, String versionProperties, String versionDescription) {

    public UrbanCodeComponent {
        componentName = componentName == null ? null : componentName.trim();
        baseDir = trimToNull(baseDir);
        fileIncludePatterns = trimToNull(fileIncludePatterns);
        fileExcludePatterns = trimToNull(fileExcludePatterns);
        versionPrefix = trimToNull(versionPrefix);
        version = trimToNull(version);
        incrementalVersion = !Boolean.FALSE.equals(incrementalVersion);
        extensions = trimToNull(extensions);
        charset = trimToNull(charset);
        pushDescription = trimToNull(pushDescription);
        versionProperties = trimToNull(versionProperties);
        versionDescription = trimToNull(versionDescription);
    }

    public static UrbanCodeComponent of(String componentName, String baseDir, String fileIncludePatterns) {
        return new UrbanCodeComponent(componentName, baseDir, fileIncludePatterns, null, null, null, true, null, null,
                null, null, null);
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
                .set("extensions", extensions)
                .set("charset", charset)
                .set("pushDescription", pushDescription)
                .set("versionProperties", versionProperties)
                .set("versionDescription", versionDescription)
                .toMap();
    }
}
