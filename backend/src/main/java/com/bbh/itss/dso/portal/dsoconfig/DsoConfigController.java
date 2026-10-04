package com.bbh.itss.dso.portal.dsoconfig;

import com.bbh.itss.dso.portal.catalog.ProductRepository;
import com.bbh.itss.dso.portal.common.NotFoundException;
import com.bbh.itss.dso.portal.pipeline.KeyRevokedException;
import com.bbh.itss.dso.portal.pipeline.Pipeline;
import com.bbh.itss.dso.portal.pipeline.PipelineService;
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

/**
 * Serves DevSecOps configuration rendered as the library's config.yaml.
 * <p>
 * {@code GET /api/dso/config/{key}} is the endpoint the DevSecOps library will call with the pipeline's
 * unique key: 200 with the configuration while the key is active, 403 once it has been invalidated and
 * 404 for a key that was never issued. The portal itself shows the same configuration by pipeline id, which
 * leaves the key's last use untouched.
 */
@RestController
public class DsoConfigController {

    static final MediaType YAML = new MediaType("application", "yaml");

    private final PipelineService pipelines;
    private final ProductRepository products;
    private final DsoConfigBuilder builder;

    public DsoConfigController(PipelineService pipelines, ProductRepository products, DsoConfigBuilder builder) {
        this.pipelines = pipelines;
        this.products = products;
        this.builder = builder;
    }

    @GetMapping("/api/dso/config/{key}")
    @Transactional
    public ResponseEntity<?> pipelineConfig(@PathVariable String key,
                                            @RequestParam(defaultValue = "yaml") String format) {
        Pipeline pipeline = pipelines.resolveKey(key);
        return render(builder.pipelineConfig(pipeline), format);
    }

    @GetMapping("/api/pipelines/{id}/config")
    @Transactional(readOnly = true)
    public ResponseEntity<?> pipelineConfigPreview(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "yaml") String format) {
        return render(builder.pipelineConfig(pipelines.pipeline(id)), format);
    }

    @GetMapping("/api/products/{id}/config")
    @Transactional(readOnly = true)
    public ResponseEntity<?> productConfig(@PathVariable Long id, @RequestParam(defaultValue = "yaml") String format) {
        return render(builder.productConfig(products.findById(id).orElseThrow(() -> NotFoundException.of("Product", id))),
                format);
    }

    @ExceptionHandler(KeyRevokedException.class)
    ProblemDetail revoked(KeyRevokedException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        detail.setTitle("Pipeline key invalidated");
        return detail;
    }

    private ResponseEntity<?> render(Map<String, Object> config, String format) {
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(config);
        }
        return ResponseEntity.ok().contentType(YAML).body(builder.toYaml(config));
    }
}
