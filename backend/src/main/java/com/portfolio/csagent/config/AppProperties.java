package com.portfolio.csagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 应用级配置：模型供应方、Agent 策略、跨域。 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();
    private Llm llm = new Llm();
    private Agent agent = new Agent();

    @Data
    public static class Cors {
        private String allowedOrigins = "*";
    }

    @Data
    public static class Llm {
        /** mock（离线内置）| openai（OpenAI 兼容网关） */
        private String provider = "mock";
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "gpt-4o-mini";
        private double temperature = 0.3;
        private int timeoutSeconds = 60;
        /** 工具调用（function calling）最大轮次，防止死循环 */
        private int maxToolRounds = 3;
    }

    @Data
    public static class Agent {
        /** 注入上下文的最近对话轮数 */
        private int historyTurns = 8;
        /** 负面情绪分数达到该阈值即自动升级 / 转人工 */
        private double negativeEscalateThreshold = 0.6;
        private String systemPrompt = "你是智能客服助手，请用简体中文礼貌专业地回答。";
    }
}
