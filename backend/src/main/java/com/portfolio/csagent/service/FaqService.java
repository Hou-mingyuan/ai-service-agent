package com.portfolio.csagent.service;

import java.util.List;

import com.portfolio.csagent.entity.Faq;
import com.portfolio.csagent.mapper.FaqMapper;
import org.springframework.stereotype.Service;

/** 知识库/FAQ 检索：关键词重叠打分的轻量匹配，命中后注入上下文供模型参考。 */
@Service
public class FaqService {

    private final FaqMapper faqMapper;

    public FaqService(FaqMapper faqMapper) {
        this.faqMapper = faqMapper;
    }

    public List<Faq> listAll() {
        return faqMapper.selectList(null);
    }

    /** 返回最匹配的一条 FAQ，无命中返回 null。 */
    public Faq bestMatch(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.toLowerCase();
        Faq best = null;
        int bestScore = 0;
        for (Faq faq : faqMapper.selectList(null)) {
            int score = score(t, faq);
            if (score > bestScore) {
                bestScore = score;
                best = faq;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private int score(String text, Faq faq) {
        int score = 0;
        if (faq.getKeywords() != null) {
            for (String kw : faq.getKeywords().split("[,，]")) {
                String k = kw.trim().toLowerCase();
                if (!k.isEmpty() && text.contains(k)) {
                    score += 2;
                }
            }
        }
        if (faq.getQuestion() != null && text.contains(faq.getQuestion().toLowerCase())) {
            score += 3;
        }
        return score;
    }
}
