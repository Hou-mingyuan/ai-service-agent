-- AI Service Agent v1 release-candidate schema.
-- Compatible with H2 MySQL mode and MySQL 8 for clean zero-key startup.

CREATE TABLE IF NOT EXISTS app_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(64)  NOT NULL,
    role            VARCHAR(24)  NOT NULL,
    enabled         TINYINT      NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, username),
    INDEX idx_user_tenant_role (tenant_id, role, enabled)
);

CREATE TABLE IF NOT EXISTS conversation (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           VARCHAR(36)  NOT NULL DEFAULT 'demo',
    session_key         VARCHAR(64)  NOT NULL,
    customer_username   VARCHAR(64)  NOT NULL,
    user_name           VARCHAR(64),
    channel             VARCHAR(32)  NOT NULL DEFAULT 'web',
    status              VARCHAR(24)  NOT NULL DEFAULT 'BOT',
    bot_enabled         TINYINT      NOT NULL DEFAULT 1,
    assigned_agent      VARCHAR(64),
    handoff_reason      VARCHAR(255),
    last_intent         VARCHAR(32),
    last_sentiment      VARCHAR(16),
    resolved            TINYINT      NOT NULL DEFAULT 0,
    handoff_at          DATETIME,
    claimed_at          DATETIME,
    first_response_at   DATETIME,
    last_message_at     DATETIME,
    version             INT          NOT NULL DEFAULT 0,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, session_key),
    INDEX idx_conversation_customer (tenant_id, customer_username, updated_at),
    INDEX idx_conversation_queue (tenant_id, status, assigned_agent, handoff_at)
);

CREATE TABLE IF NOT EXISTS message (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id             VARCHAR(36)  NOT NULL DEFAULT 'demo',
    conversation_id       BIGINT       NOT NULL,
    client_message_id     VARCHAR(64),
    role                  VARCHAR(16)  NOT NULL,
    sender_username       VARCHAR(64),
    sender_name           VARCHAR(64),
    content               TEXT,
    delivery_status       VARCHAR(24)  NOT NULL DEFAULT 'SENT',
    read_at               DATETIME,
    intent                VARCHAR(32),
    intent_confidence     DECIMAL(5,4),
    sentiment             VARCHAR(16),
    sentiment_score       DECIMAL(5,4),
    classification_source VARCHAR(24),
    tool_name             VARCHAR(64),
    tool_args             TEXT,
    tool_result           TEXT,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, client_message_id),
    INDEX idx_message_conversation (tenant_id, conversation_id, id),
    INDEX idx_message_unread (tenant_id, conversation_id, read_at, id)
);

CREATE TABLE IF NOT EXISTS ticket (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id         VARCHAR(36)  NOT NULL DEFAULT 'demo',
    ticket_no         VARCHAR(40)  NOT NULL,
    idempotency_key   VARCHAR(96),
    conversation_id   BIGINT,
    category          VARCHAR(32)  NOT NULL DEFAULT 'OTHER',
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    priority          VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    status            VARCHAR(24)  NOT NULL DEFAULT 'OPEN',
    assignee          VARCHAR(64),
    source            VARCHAR(24)  NOT NULL DEFAULT 'AGENT',
    customer          VARCHAR(64),
    resolution_note   VARCHAR(1000),
    close_reason      VARCHAR(1000),
    sla_due_at        DATETIME,
    sla_warning_at    DATETIME,
    sla_breached_at   DATETIME,
    escalated_at      DATETIME,
    version           INT          NOT NULL DEFAULT 0,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at         DATETIME,
    UNIQUE (tenant_id, ticket_no),
    UNIQUE (tenant_id, idempotency_key),
    INDEX idx_ticket_queue (tenant_id, status, assignee, priority, created_at),
    INDEX idx_ticket_conversation (tenant_id, conversation_id, created_at),
    INDEX idx_ticket_sla (tenant_id, status, sla_due_at, sla_breached_at)
);

CREATE TABLE IF NOT EXISTS ticket_event (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   VARCHAR(36)  NOT NULL DEFAULT 'demo',
    ticket_id   BIGINT       NOT NULL,
    event_type  VARCHAR(48)  NOT NULL DEFAULT 'STATUS_CHANGED',
    event_key   VARCHAR(96),
    from_status VARCHAR(24),
    to_status   VARCHAR(24),
    note        VARCHAR(1000),
    operator    VARCHAR(64),
    actor_role  VARCHAR(24),
    metadata_json TEXT,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (ticket_id, event_key),
    INDEX idx_ticket_event_timeline (tenant_id, ticket_id, id)
);

CREATE TABLE IF NOT EXISTS order_info (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL DEFAULT 'demo',
    owner_username  VARCHAR(64)  NOT NULL DEFAULT 'customer',
    order_no        VARCHAR(32)  NOT NULL,
    customer        VARCHAR(64),
    product         VARCHAR(128),
    amount          DECIMAL(12,2),
    status          VARCHAR(24),
    address         VARCHAR(255),
    adapter_source  VARCHAR(24)  NOT NULL DEFAULT 'mock',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, order_no),
    INDEX idx_order_owner (tenant_id, owner_username, created_at)
);

CREATE TABLE IF NOT EXISTS shipment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL DEFAULT 'demo',
    owner_username  VARCHAR(64)  NOT NULL DEFAULT 'customer',
    order_no        VARCHAR(32)  NOT NULL,
    tracking_no     VARCHAR(48),
    carrier         VARCHAR(48),
    status          VARCHAR(24),
    last_location   VARCHAR(128),
    adapter_source  VARCHAR(24)  NOT NULL DEFAULT 'mock',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_shipment_order (tenant_id, owner_username, order_no),
    INDEX idx_shipment_tracking (tenant_id, tracking_no)
);

CREATE TABLE IF NOT EXISTS policy (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id         VARCHAR(36)  NOT NULL DEFAULT 'demo',
    owner_username    VARCHAR(64)  NOT NULL DEFAULT 'customer',
    policy_no         VARCHAR(32)  NOT NULL,
    holder            VARCHAR(64),
    product           VARCHAR(128),
    premium           DECIMAL(12,2),
    status            VARCHAR(24),
    effective_date    VARCHAR(32),
    expire_date       VARCHAR(32),
    next_payment_date VARCHAR(32),
    adapter_source    VARCHAR(24)  NOT NULL DEFAULT 'mock',
    UNIQUE (tenant_id, policy_no),
    INDEX idx_policy_owner (tenant_id, owner_username, policy_no)
);

CREATE TABLE IF NOT EXISTS feedback (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL DEFAULT 'demo',
    conversation_id BIGINT       NOT NULL,
    customer_username VARCHAR(64) NOT NULL,
    rating          INT          NOT NULL,
    comment         VARCHAR(512),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, conversation_id),
    INDEX idx_feedback_created (tenant_id, created_at)
);

CREATE TABLE IF NOT EXISTS knowledge_document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    source_uri      VARCHAR(500),
    content         TEXT         NOT NULL,
    checksum        VARCHAR(64)  NOT NULL,
    status          VARCHAR(24)  NOT NULL DEFAULT 'READY',
    created_by      VARCHAR(64)  NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, checksum),
    INDEX idx_knowledge_document (tenant_id, status, updated_at)
);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    document_id     BIGINT       NOT NULL,
    chunk_index     INT          NOT NULL,
    content         TEXT         NOT NULL,
    search_text     TEXT         NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_id, chunk_index),
    INDEX idx_knowledge_chunk_document (tenant_id, document_id, chunk_index)
);

CREATE TABLE IF NOT EXISTS tool_execution (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id         VARCHAR(36)  NOT NULL,
    execution_key     VARCHAR(96)  NOT NULL,
    conversation_id   BIGINT       NOT NULL,
    customer_username VARCHAR(64)  NOT NULL,
    tool_name         VARCHAR(64)  NOT NULL,
    arguments_json    TEXT         NOT NULL,
    result_json       TEXT,
    summary           VARCHAR(1500),
    status            VARCHAR(32)  NOT NULL,
    is_sensitive      TINYINT      NOT NULL DEFAULT 0,
    adapter_source    VARCHAR(32),
    confirmed_by      VARCHAR(64),
    confirmed_at      DATETIME,
    duration_ms       BIGINT,
    error_code        VARCHAR(64),
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, execution_key),
    INDEX idx_tool_execution_conversation (tenant_id, conversation_id, id),
    INDEX idx_tool_execution_status (tenant_id, status, created_at)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    request_id      VARCHAR(64)  NOT NULL,
    actor_username  VARCHAR(64)  NOT NULL,
    actor_role      VARCHAR(24)  NOT NULL,
    action          VARCHAR(64)  NOT NULL,
    resource_type   VARCHAR(48)  NOT NULL,
    resource_id     VARCHAR(96),
    outcome         VARCHAR(24)  NOT NULL,
    idempotency_key VARCHAR(96),
    details_json    TEXT,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_actor (tenant_id, actor_username, created_at),
    INDEX idx_audit_resource (tenant_id, resource_type, resource_id, created_at),
    INDEX idx_audit_request (tenant_id, request_id)
);

CREATE TABLE IF NOT EXISTS realtime_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    audience_type   VARCHAR(24)  NOT NULL,
    audience_id     VARCHAR(64),
    type            VARCHAR(64)  NOT NULL,
    payload_json    TEXT         NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_realtime_replay (tenant_id, audience_type, audience_id, id)
);

-- Legacy FAQ remains readable during the v1 transition; new retrieval uses document/chunk tables.
CREATE TABLE IF NOT EXISTS faq (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    question  VARCHAR(255) NOT NULL,
    answer    TEXT,
    keywords  VARCHAR(255),
    category  VARCHAR(32)
);
