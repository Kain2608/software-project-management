package com.smartelectronicsupermarket.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartelectronicsupermarket.ai.summary.AiSpecSummaryService;
import com.smartelectronicsupermarket.ai.summary.SpecSummaryRequest;
import com.smartelectronicsupermarket.ai.summary.SpecSummaryResult;
import com.smartelectronicsupermarket.common.web.TrustedActorAccess;

/** FR-09: JWT required (no permitAll); ai.use is checked per store inside the service. */
@RestController
@RequestMapping("/api/v1/ai")
public class AiSpecSummaryController {

    private final AiSpecSummaryService service;

    public AiSpecSummaryController(AiSpecSummaryService service) {
        this.service = service;
    }

    @PostMapping("/summaries/specifications")
    public ResponseEntity<SpecSummaryResult> summarize(
            @RequestBody SpecSummaryRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.summarize(TrustedActorAccess.actorId(authentication), request));
    }
}
