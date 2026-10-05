package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.dsoconfig.port.in.RenderConfigUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DsoConfigController {

    static final MediaType YAML = new MediaType("application", "yaml");

    private final RenderConfigUseCase configs;
    private final ConfigYaml yaml = new ConfigYaml();

    public DsoConfigController(RenderConfigUseCase configs) {
        this.configs = configs;
    }

    @GetMapping("/api/dso/config/{key}")
    public ResponseEntity<?> pipelineConfig(@PathVariable String key,
                                            @RequestParam(defaultValue = "yaml") String format) {
        return render(configs.readByKey(key), format);
    }

    @GetMapping("/api/pipelines/{id}/config")
    public ResponseEntity<?> pipelineConfigPreview(@PathVariable long id,
                                                   @RequestParam(defaultValue = "yaml") String format) {
        return render(configs.pipelineConfig(id), format);
    }

    @GetMapping("/api/products/{id}/config")
    public ResponseEntity<?> productConfig(@PathVariable long id, @RequestParam(defaultValue = "yaml") String format) {
        return render(configs.productConfig(id), format);
    }

    @GetMapping("/api/settings/config")
    public ResponseEntity<?> globalConfig(@RequestParam(defaultValue = "yaml") String format) {
        return render(configs.settingsConfig(), format);
    }

    private ResponseEntity<?> render(Map<String, Object> config, String format) {
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(config);
        }
        return ResponseEntity.ok().contentType(YAML).body(yaml.render(config));
    }
}
