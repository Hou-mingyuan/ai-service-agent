package com.portfolio.csagent.controller;

import java.util.List;

import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.dto.KnowledgeDocumentRequest;
import com.portfolio.csagent.entity.KnowledgeDocument;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.Permission;
import com.portfolio.csagent.service.KnowledgeSearchResult;
import com.portfolio.csagent.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeService knowledgeService;
    private final CurrentActor currentActor;

    public KnowledgeController(KnowledgeService knowledgeService, CurrentActor currentActor) {
        this.knowledgeService = knowledgeService;
        this.currentActor = currentActor;
    }

    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('" + Permission.KNOWLEDGE_READ + "')")
    public ApiResponse<List<KnowledgeDocument>> list() {
        return ApiResponse.ok(knowledgeService.list(currentActor.require()));
    }

    @PostMapping("/documents")
    @PreAuthorize("hasAuthority('" + Permission.KNOWLEDGE_WRITE + "')")
    public ApiResponse<KnowledgeDocument> ingest(@Valid @RequestBody KnowledgeDocumentRequest request) {
        return ApiResponse.ok(knowledgeService.ingest(currentActor.require(), request));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('" + Permission.KNOWLEDGE_WRITE + "')")
    public ApiResponse<KnowledgeDocument> archive(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeService.archive(currentActor.require(), id));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('" + Permission.KNOWLEDGE_READ + "')")
    public ApiResponse<List<KnowledgeSearchResult>> search(@RequestParam String q,
                                                           @RequestParam(defaultValue = "3") int topK) {
        AuthenticatedUser actor = currentActor.require();
        return ApiResponse.ok(knowledgeService.search(actor.tenantId(), q, topK, 0.2));
    }
}
