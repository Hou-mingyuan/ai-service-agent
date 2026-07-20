package com.portfolio.csagent.agent;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class PromptSafetyService {
    private static final List<String> SUSPICIOUS = List.of(
            "忽略之前", "忽略以上", "系统提示词", "开发者消息", "显示密钥", "泄露密钥",
            "绕过权限", "越权访问", "ignore previous", "ignore all previous", "system prompt",
            "developer message", "reveal secret", "jailbreak", "<script", "data:text/html");

    public SafetyResult inspect(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String marker : SUSPICIOUS) {
            if (normalized.contains(marker)) {
                return new SafetyResult(false, "PROMPT_INJECTION", marker);
            }
        }
        return new SafetyResult(true, "OK", null);
    }

    public record SafetyResult(boolean allowed, String code, String marker) {
    }
}
