package com.bbh.itss.dso.portal.dsoconfig;

import com.bbh.itss.dso.portal.application.catalog.port.in.QueryProductsUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelineKeysUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelineView;
import com.bbh.itss.dso.portal.application.pipeline.port.in.QueryPipelinesUseCase;
import com.bbh.itss.dso.portal.domain.pipeline.KeyRevokedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DsoConfigController {

    static final MediaType YAML = new MediaType("application", "yaml");

    private final ManagePipelineKeysUseCase keys;
    private final QueryPipelinesUseCase pipelines;
    private final QueryProductsUseCase products;
    private final DsoConfigBuilder builder;

    public DsoConfigController(ManagePipelineKeysUseCase keys, QueryPipelinesUseCase pipelines,
                               QueryProductsUseCase products, DsoConfigBuilder builder) {
        this.keys = keys;
        this.pipelines = pipelines;
        this.products = products;
        this.builder = builder;
    }

    @GetMapping("/api/dso/config/{key}")
    @Transactional
    public ResponseEntity<?> pipelineConfig(@PathVariable String key,
                                            @RequestParam(defaultValue = "yaml") String format) {
        return render(config(keys.resolveKey(key)), format);
    }

    @GetMapping("/api/pipelines/{id}/config")
    @Transactional(readOnly = true)
    public ResponseEntity<?> pipelineConfigPreview(@PathVariable long id,
                                                   @RequestParam(defaultValue = "yaml") String format) {
        return render(config(pipelines.get(id)), format);
    }

    @GetMapping("/api/products/{id}/config")
    @Transactional(readOnly = true)
    public ResponseEntity<?> productConfig(@PathVariable long id, @RequestParam(defaultValue = "yaml") String format) {
        return render(builder.productConfig(products.get(id)), format);
    }

    @GetMapping("/api/settings/config")
    public ResponseEntity<?> globalConfig(@RequestParam(defaultValue = "yaml") String format) {
        return render(builder.globalConfig(), format);
    }

    @ExceptionHandler(KeyRevokedException.class)
    ProblemDetail revoked(KeyRevokedException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        detail.setTitle("Pipeline key invalidated");
        return detail;
    }

    private Map<String, Object> config(PipelineView view) {
        return builder.pipelineConfig(view.product(), view.service(), view.pipeline());
    }

    private ResponseEntity<?> render(Map<String, Object> config, String format) {
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(config);
        }
        return ResponseEntity.ok().contentType(YAML).body(builder.toYaml(config));
    }
}
