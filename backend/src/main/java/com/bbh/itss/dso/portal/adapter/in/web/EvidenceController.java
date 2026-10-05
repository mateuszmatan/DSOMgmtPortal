package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.application.evidence.port.in.PipelineEvidence;
import com.bbh.itss.dso.portal.application.evidence.port.in.ProductEvidence;
import com.bbh.itss.dso.portal.application.evidence.port.in.QueryEvidenceUseCase;
import com.bbh.itss.dso.portal.application.evidence.port.in.ServiceEvidence;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import com.bbh.itss.dso.portal.domain.catalog.TestStage;
import com.bbh.itss.dso.portal.domain.evidence.CheckStatus;
import com.bbh.itss.dso.portal.domain.evidence.EvidenceScanner;
import com.bbh.itss.dso.portal.domain.monitoring.RunResult;
import com.bbh.itss.dso.portal.domain.pipeline.Pipeline;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final QueryEvidenceUseCase evidence;

    public EvidenceController(QueryEvidenceUseCase evidence) {
        this.evidence = evidence;
    }

    @GetMapping("/products/{id}")
    public ProductEvidenceResponse product(@PathVariable long id) {
        ProductEvidence found = evidence.product(id);
        Product product = found.product();
        return new ProductEvidenceResponse(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), product.contactEmail(),
                found.services().stream().map(ServiceEvidenceResponse::of).toList(), found.metricsError());
    }

    public record ProductEvidenceResponse(Long productId, String code, String name, String description,
                                          String ownerTeam, String contactEmail,
                                          List<ServiceEvidenceResponse> services, String metricsError) {
    }

    public record ServiceEvidenceResponse(Long serviceId, String name, String description, String repositoryUrl,
                                          String artifactName, String appScanApplicationId, String sonarProjectKey,
                                          String nexusIqApplication, List<PipelineEvidenceResponse> pipelines) {

        static ServiceEvidenceResponse of(ServiceEvidence evidence) {
            Service service = evidence.service();
            ServiceSettings settings = service.settings();
            return new ServiceEvidenceResponse(service.id(), service.name(), service.description(),
                    settings.scm().repositoryUrl(), settings.deployment().artifactName(),
                    settings.appScan().applicationId(), settings.sonar().projectKey(),
                    settings.nexusIq().application(),
                    evidence.pipelines().stream().map(PipelineEvidenceResponse::of).toList());
        }
    }

    public record PipelineEvidenceResponse(Long pipelineId, PipelineType type, boolean enabled, String jenkinsJobUrl,
                                           RunResult status, RunEvidence run) {

        static PipelineEvidenceResponse of(PipelineEvidence evidence) {
            Pipeline pipeline = evidence.pipeline();
            return new PipelineEvidenceResponse(pipeline.id(), pipeline.type(), pipeline.isEnabled(),
                    evidence.jenkinsJobUrl(), evidence.status(), RecordMapper.map(evidence.run(), RunEvidence.class));
        }
    }

    public record RunEvidence(Build build, Coverage coverage, List<TestSuite> testSuites, List<Scan> scans,
                              ReleaseGate releaseGate, List<Stage> stages) {
    }

    public record Build(Long number, Instant finishedAt, RunResult result, String branch, String commit,
                        Long durationSeconds, String job, String url, String reportUrl, String testReportUrl,
                        String artifactsUrl) {
    }

    public record Coverage(CheckStatus status, Double linePercent, Double requiredPercent, Long coveredLines,
                           Long totalLines) {
    }

    public record TestSuite(TestStage stage, CheckStatus status, Long jobs, Long passed, Long failed,
                            Long notConfigured, Long durationMs) {
    }

    public record Scan(EvidenceScanner scanner, CheckStatus status, Long critical, Long high, Long medium, Long low,
                       Long maxCritical, Long maxHigh, Long maxMedium, String link) {
    }

    public record ReleaseGate(boolean allowed, Long violations, String reason) {
    }

    public record Stage(String name, CheckStatus status, Long durationSeconds, String reason) {
    }
}
