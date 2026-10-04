package com.bbh.itss.dso.portal.evidence;

import com.bbh.itss.dso.portal.evidence.EvidenceDtos.ProductEvidence;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final EvidenceService evidence;

    public EvidenceController(EvidenceService evidence) {
        this.evidence = evidence;
    }

    @GetMapping("/products/{id}")
    public ProductEvidence product(@PathVariable Long id) {
        return evidence.product(id);
    }
}
