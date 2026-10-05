package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelineKeysUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.ManagePipelinesUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.QueryPipelinesUseCase;
import com.bbh.itss.dso.portal.application.pipeline.port.in.ServicePipelinesView;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.Service;
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

    private final QueryPipelinesUseCase queries;
    private final ManagePipelinesUseCase pipelines;
    private final ManagePipelineKeysUseCase keys;

    public PipelineController(QueryPipelinesUseCase queries, ManagePipelinesUseCase pipelines,
                              ManagePipelineKeysUseCase keys) {
        this.queries = queries;
        this.pipelines = pipelines;
        this.keys = keys;
    }

    @GetMapping("/products/{productId}/pipelines")
    public List<ServicePipelinesResponse> listForProduct(@PathVariable long productId) {
        return queries.listForProduct(productId).stream().map(ServicePipelinesResponse::of).toList();
    }

    @PostMapping("/services/{serviceId}/pipelines")
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineResponse create(@PathVariable long serviceId, @Valid @RequestBody PipelineRequest request) {
        return PipelineResponse.withKeys(pipelines.create(serviceId, request.toCommand()));
    }

    @GetMapping("/pipelines/{id}")
    public PipelineResponse get(@PathVariable long id) {
        return PipelineResponse.withKeys(queries.get(id));
    }

    @PutMapping("/pipelines/{id}")
    public PipelineResponse update(@PathVariable long id, @Valid @RequestBody PipelineRequest request) {
        return PipelineResponse.withKeys(pipelines.update(id, request.toCommand()));
    }

    @DeleteMapping("/pipelines/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        pipelines.delete(id);
    }

    @PostMapping("/pipelines/{id}/keys/revoke")
    public PipelineResponse revokeKey(@PathVariable long id, @Valid @RequestBody RevokeKeyRequest request) {
        return PipelineResponse.withKeys(keys.revokeKey(id, request.reason()));
    }

    @PostMapping("/pipelines/{id}/keys")
    public PipelineResponse issueKey(@PathVariable long id) {
        return PipelineResponse.withKeys(keys.issueKey(id));
    }

    public record ServicePipelinesResponse(Long serviceId, String serviceName, String description, BuildTool buildTool,
                                           DeployTarget deployTarget, List<PipelineResponse> pipelines) {

        static ServicePipelinesResponse of(ServicePipelinesView view) {
            Service service = view.service();
            return new ServicePipelinesResponse(service.id(), service.name(), service.description(),
                    service.settings().build().tool(), service.settings().deployment().target(),
                    view.pipelines().stream().map(PipelineResponse::summary).toList());
        }
    }
}
