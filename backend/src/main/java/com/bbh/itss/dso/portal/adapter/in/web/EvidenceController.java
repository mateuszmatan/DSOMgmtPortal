package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.evidence.port.in.QueryEvidenceUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final QueryEvidenceUseCase evidence;

    public EvidenceController(QueryEvidenceUseCase evidence) {
        this.evidence = evidence;
    }

    @GetMapping("/products/{id}")
    public ProductEvidenceResponse product(@PathVariable long id) {
        return ProductEvidenceResponse.from(evidence.product(id));
    }
}
