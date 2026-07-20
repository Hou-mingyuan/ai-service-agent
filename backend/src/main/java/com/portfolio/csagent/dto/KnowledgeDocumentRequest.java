package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeDocumentRequest(
        @NotBlank(message = "文档标题不能为空") @Size(max = 255, message = "文档标题不能超过255字符") String title,
        @Size(max = 500, message = "来源地址不能超过500字符") String sourceUri,
        @NotBlank(message = "文档内容不能为空") @Size(max = 100000, message = "单文档不能超过100000字符") String content) {
}
