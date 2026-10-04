package com.bbh.itss.dso.portal.pipeline;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PipelineController {

    private final PipelineService pipelines;

    public PipelineController(PipelineService pipelines) {
        this.pipelines = pipelines;
    }

    @GetMapping("/products/{productId}/pipelines")
    public List<ServicePipelines> listForProduct(@PathVariable Long productId) {
        return pipelines.listForProduct(productId);
    }

    @PostMapping("/services/{serviceId}/pipelines")
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineResponse create(@PathVariable Long serviceId, @Valid @RequestBody PipelineRequest request) {
        return pipelines.create(serviceId, request);
    }

    @GetMapping("/pipelines/{id}")
    public PipelineResponse get(@PathVariable Long id) {
        return pipelines.get(id);
    }

    @PutMapping("/pipelines/{id}")
    public PipelineResponse update(@PathVariable Long id, @Valid @RequestBody PipelineRequest request) {
        return pipelines.update(id, request);
    }

    @DeleteMapping("/pipelines/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        pipelines.delete(id);
    }

    @PostMapping("/pipelines/{id}/keys/revoke")
    public PipelineResponse revokeKey(@PathVariable Long id, @Valid @RequestBody RevokeKeyRequest request) {
        return pipelines.revokeKey(id, request.reason());
    }

    @PostMapping("/pipelines/{id}/keys")
    public PipelineResponse issueKey(@PathVariable Long id) {
        return pipelines.issueKey(id);
    }
}
