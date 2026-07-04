package com.portfolio.csagent.controller;

import java.util.List;

import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.entity.Faq;
import com.portfolio.csagent.service.FaqService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faqs")
public class FaqController {

    private final FaqService faqService;

    public FaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    @GetMapping
    public ApiResponse<List<Faq>> list() {
        return ApiResponse.ok(faqService.listAll());
    }
}
