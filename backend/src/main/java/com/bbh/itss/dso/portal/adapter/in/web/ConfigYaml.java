package com.bbh.itss.dso.portal.adapter.in.web;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.util.Map;

final class ConfigYaml {

    private final DumperOptions options = options();

    String render(Map<String, Object> config) {
        return new Yaml(new Representer(options), options).dump(config);
    }

    private static DumperOptions options() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setPrettyFlow(true);
        options.setWidth(160);
        return options;
    }
}
