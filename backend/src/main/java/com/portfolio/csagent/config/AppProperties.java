package com.portfolio.csagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 应用级配置：模型供应方、Agent 策略、跨域。 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String timeZone = "Asia/Shanghai";
    private Cors cors = new Cors();
    private Llm llm = new Llm();
    private Agent agent = new Agent();
    private Business business = new Business();
    private Demo demo = new Demo();
    private Sla sla = new Sla();

    @Data
    public static class Cors {
        private String allowedOrigins = "http://127.0.0.1:19041,http://localhost:19041";
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
        private boolean allowPrivateBaseUrl = false;
    }

    @Data
    public static class Agent {
        /** 注入上下文的最近对话轮数 */
        private int historyTurns = 8;
        /** 负面情绪分数达到该阈值即自动升级 / 转人工 */
        private double negativeEscalateThreshold = 0.6;
        private String systemPrompt = "你是智能客服助手，请用简体中文礼貌专业地回答。";
        private int knowledgeTopK = 3;
        private double knowledgeMinScore = 0.2;
        private int sseTimeoutSeconds = 90;
    }

    @Data
    public static class Business {
        /** mock（本地演示数据）| http（真实业务系统） */
        private String adapter = "mock";
        private String baseUrl = "";
        private String apiToken = "";
        private int timeoutSeconds = 5;
        private int maxRetries = 1;
    }

    @Data
    public static class Demo {
        private boolean enabled = true;
        private String tenantId = "demo";
    }

    @Data
    public static class Sla {
        private int urgentMinutes = 15;
        private int highMinutes = 60;
        private int mediumMinutes = 240;
        private int lowMinutes = 480;
        private int warningMinutes = 5;
        private long scanIntervalMs = 30000;
    }
}
