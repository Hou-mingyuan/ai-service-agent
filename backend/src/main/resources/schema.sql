-- ======================================================================
-- AI Service Agent 数据库结构（兼容 H2[MySQL 模式] 与 MySQL 8）
-- 表结构使用 IF NOT EXISTS，可重复执行；示例数据由 DataSeeder 幂等写入。
-- ======================================================================

CREATE TABLE IF NOT EXISTS conversation (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_key  VARCHAR(64)  NOT NULL,
    user_name    VARCHAR(64),
    channel      VARCHAR(32)  DEFAULT 'web',
    status       VARCHAR(24)  DEFAULT 'BOT',      -- BOT / HUMAN_PENDING / HUMAN / CLOSED
    last_intent  VARCHAR(32),
    last_sentiment VARCHAR(16),
    resolved     TINYINT      DEFAULT 0,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(16)  NOT NULL,        -- user / assistant / tool / agent / system
    content         TEXT,
    intent          VARCHAR(32),
    sentiment       VARCHAR(16),
    sentiment_score DECIMAL(4,3),
    tool_name       VARCHAR(64),
    tool_args       TEXT,
    tool_result     TEXT,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ticket (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_no       VARCHAR(32)  NOT NULL,
    conversation_id BIGINT,
    category        VARCHAR(32),                  -- ORDER / LOGISTICS / POLICY / REFUND / COMPLAINT / OTHER
    title           VARCHAR(255),
    description     TEXT,
    priority        VARCHAR(16)  DEFAULT 'MEDIUM',-- LOW / MEDIUM / HIGH / URGENT
    status          VARCHAR(24)  DEFAULT 'OPEN',  -- OPEN / IN_PROGRESS / PENDING / RESOLVED / CLOSED
    assignee        VARCHAR(64),
    source          VARCHAR(24)  DEFAULT 'AGENT', -- AGENT(机器人自动) / HUMAN / SYSTEM
    customer        VARCHAR(64),
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    closed_at       DATETIME
);

CREATE TABLE IF NOT EXISTS ticket_event (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT       NOT NULL,
    from_status VARCHAR(24),
    to_status   VARCHAR(24),
    note        VARCHAR(512),
    operator    VARCHAR(64),
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_info (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no    VARCHAR(32)  NOT NULL,
    customer    VARCHAR(64),
    product     VARCHAR(128),
    amount      DECIMAL(12,2),
    status      VARCHAR(24),                      -- PAID / SHIPPED / DELIVERED / REFUNDING / CANCELLED
    address     VARCHAR(255),
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS shipment (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no      VARCHAR(32)  NOT NULL,
    tracking_no   VARCHAR(48),
    carrier       VARCHAR(48),
    status        VARCHAR(24),                    -- PENDING / IN_TRANSIT / OUT_FOR_DELIVERY / SIGNED
    last_location VARCHAR(128),
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS policy (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_no         VARCHAR(32)  NOT NULL,
    holder            VARCHAR(64),
    product           VARCHAR(128),
    premium           DECIMAL(12,2),
    status            VARCHAR(24),                -- ACTIVE / LAPSED / EXPIRED / CANCELLED
    effective_date    VARCHAR(32),
    expire_date       VARCHAR(32),
    next_payment_date VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS faq (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    question  VARCHAR(255) NOT NULL,
    answer    TEXT,
    keywords  VARCHAR(255),
    category  VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS feedback (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT       NOT NULL,
    rating          INT,                          -- 1..5
    comment         VARCHAR(512),
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP
);
