package com.portfolio.csagent.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.dto.KnowledgeDocumentRequest;
import com.portfolio.csagent.entity.KnowledgeChunk;
import com.portfolio.csagent.entity.KnowledgeDocument;
import com.portfolio.csagent.mapper.KnowledgeChunkMapper;
import com.portfolio.csagent.mapper.KnowledgeDocumentMapper;
import com.portfolio.csagent.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {
    private static final Pattern LATIN_WORD = Pattern.compile("[a-z0-9_-]{2,}");
    private static final Pattern CJK = Pattern.compile("[\\p{IsHan}]+");
    private static final List<String> QUERY_FILLERS = List.of(
            "请问", "帮我", "一下", "需要", "满足", "什么", "哪些", "条件", "要求",
            "怎么", "如何", "是否", "可以", "能不能", "相关", "一下子");
    private static final int CHUNK_SIZE = 700;
    private static final int CHUNK_OVERLAP = 80;

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final AuditService auditService;

    public KnowledgeService(KnowledgeDocumentMapper documentMapper, KnowledgeChunkMapper chunkMapper,
                            AuditService auditService) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.auditService = auditService;
    }

    @Transactional
    public KnowledgeDocument ingest(AuthenticatedUser actor, KnowledgeDocumentRequest request) {
        String content = request.content().trim();
        String checksum = sha256(content);
        KnowledgeDocument existing = documentMapper.selectOne(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getTenantId, actor.tenantId())
                .eq(KnowledgeDocument::getChecksum, checksum)
                .last("limit 1"));
        if (existing != null) {
            auditService.record(actor, "KNOWLEDGE_INGEST", "KNOWLEDGE_DOCUMENT", existing.getId(),
                    "IDEMPOTENT_REPLAY", checksum, Map.of("title", request.title()));
            return existing;
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setTenantId(actor.tenantId());
        document.setTitle(request.title().trim());
        document.setSourceUri(blankToNull(request.sourceUri()));
        document.setContent(content);
        document.setChecksum(checksum);
        document.setStatus("READY");
        document.setCreatedBy(actor.username());
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);

        List<String> chunks = chunk(content);
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setTenantId(actor.tenantId());
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(index);
            chunk.setContent(chunks.get(index));
            chunk.setSearchText(normalize(document.getTitle() + " " + chunks.get(index)));
            chunk.setCreatedAt(LocalDateTime.now());
            chunkMapper.insert(chunk);
        }
        auditService.record(actor, "KNOWLEDGE_INGEST", "KNOWLEDGE_DOCUMENT", document.getId(),
                "SUCCESS", checksum, Map.of("title", document.getTitle(), "chunks", chunks.size()));
        return document;
    }

    public List<KnowledgeDocument> list(AuthenticatedUser actor) {
        return documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getTenantId, actor.tenantId())
                .orderByDesc(KnowledgeDocument::getUpdatedAt));
    }

    @Transactional
    public KnowledgeDocument archive(AuthenticatedUser actor, Long id) {
        KnowledgeDocument document = requireDocument(actor.tenantId(), id);
        if (!"ARCHIVED".equals(document.getStatus())) {
            document.setStatus("ARCHIVED");
            document.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(document);
            auditService.record(actor, "KNOWLEDGE_ARCHIVE", "KNOWLEDGE_DOCUMENT", id,
                    "SUCCESS", null, Map.of("title", document.getTitle()));
        }
        return document;
    }

    public List<KnowledgeSearchResult> search(String tenantId, String query, int topK, double minScore) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<KnowledgeDocument> documents = documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getTenantId, tenantId)
                .eq(KnowledgeDocument::getStatus, "READY"));
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeDocument> byId = new HashMap<>();
        documents.forEach(document -> byId.put(document.getId(), document));
        String normalizedQuery = normalize(query);
        Set<String> queryTokens = tokens(normalizedQuery);

        return chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getTenantId, tenantId)
                        .in(KnowledgeChunk::getDocumentId, byId.keySet()))
                .stream()
                .map(chunk -> result(chunk, byId.get(chunk.getDocumentId()), normalizedQuery, queryTokens))
                .filter(result -> result.score() >= minScore)
                .sorted(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed()
                        .thenComparing(KnowledgeSearchResult::chunkId))
                .limit(Math.min(10, Math.max(1, topK)))
                .toList();
    }

    private KnowledgeSearchResult result(KnowledgeChunk chunk, KnowledgeDocument document,
                                         String normalizedQuery, Set<String> queryTokens) {
        String haystack = chunk.getSearchText() == null ? normalize(chunk.getContent()) : chunk.getSearchText();
        Set<String> chunkTokens = tokens(haystack);
        long overlap = queryTokens.stream().filter(chunkTokens::contains).count();
        double tokenScore = queryTokens.isEmpty() ? 0 : (double) overlap / queryTokens.size();
        double exactBonus = !normalizedQuery.isBlank() && haystack.contains(normalizedQuery) ? 0.35 : 0;
        double titleBonus = normalize(document.getTitle()).contains(normalizedQuery) ? 0.15 : 0;
        double score = Math.min(1.0, tokenScore * 0.65 + exactBonus + titleBonus);
        String citation = "KB-" + document.getId() + "-" + (chunk.getChunkIndex() + 1);
        return new KnowledgeSearchResult(citation, document.getId(), chunk.getId(), document.getTitle(),
                document.getSourceUri(), excerpt(chunk.getContent()), round(score), "local-lexical");
    }

    private Set<String> tokens(String text) {
        String informative = text;
        for (String filler : QUERY_FILLERS) {
            informative = informative.replace(filler, " ");
        }
        Set<String> result = new HashSet<>();
        Matcher words = LATIN_WORD.matcher(informative);
        while (words.find()) {
            result.add(words.group());
        }
        Matcher cjk = CJK.matcher(informative);
        while (cjk.find()) {
            String run = cjk.group();
            if (run.length() == 1) {
                result.add(run);
            } else {
                for (int i = 0; i < run.length() - 1; i++) {
                    result.add(run.substring(i, i + 2));
                }
            }
        }
        return result;
    }

    private List<String> chunk(String content) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + CHUNK_SIZE);
            if (end < content.length()) {
                int boundary = Math.max(content.lastIndexOf('\n', end), content.lastIndexOf('。', end));
                if (boundary > start + CHUNK_SIZE / 2) {
                    end = boundary + 1;
                }
            }
            result.add(content.substring(start, end).trim());
            if (end >= content.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return result;
    }

    private KnowledgeDocument requireDocument(String tenantId, Long id) {
        KnowledgeDocument document = documentMapper.selectOne(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getTenantId, tenantId)
                .eq(KnowledgeDocument::getId, id)
                .last("limit 1"));
        if (document == null) {
            throw new BizException(404, "知识文档不存在");
        }
        return document;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9_-]+", " ").trim();
    }

    private String excerpt(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= 360 ? trimmed : trimmed.substring(0, 360) + "...";
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to checksum document", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
