-- V1: Khởi tạo schema CtV Flow Tracer — khớp docs/diagrams/database.dbml và .context/database.md
-- KHÔNG sửa file này sau khi đã chạy; thay đổi cấu trúc bằng migration mới (V2__..., V3__...).

-- ---------------- Enums ----------------
CREATE TYPE source_role       AS ENUM ('BACKEND', 'FRONTEND', 'LEGACY');
CREATE TYPE scan_trigger      AS ENUM ('IMPORT', 'RESCAN');
CREATE TYPE scan_status       AS ENUM ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED');
CREATE TYPE parse_status      AS ENUM ('OK', 'ERROR', 'SKIPPED');
CREATE TYPE edge_confidence   AS ENUM ('CERTAIN', 'INFERRED', 'AI_RESOLVED', 'UNRESOLVED', 'RUNTIME_VERIFIED');
CREATE TYPE override_decision AS ENUM ('PIN', 'REJECT');
CREATE TYPE ai_purpose        AS ENUM ('PURPOSE_TEXT', 'SCENARIO_POLISH', 'MOCK_DATA', 'EDGE_RESOLUTION',
                                       'LEGACY_MATCH', 'FEATURE_SUMMARY', 'MIGRATION_CHECKLIST');
CREATE TYPE llm_call_status   AS ENUM ('OK', 'ERROR', 'REJECTED_BY_VALIDATION', 'QUOTA_EXCEEDED');
CREATE TYPE feature_origin    AS ENUM ('USER', 'SUGGESTED');

-- ---------------- Cấu hình phân tích ----------------
CREATE TABLE workspaces (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             varchar(200) NOT NULL UNIQUE,
    description      text,
    settings         jsonb        NOT NULL DEFAULT '{}',
    last_scan_run_id uuid,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE sources (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name             varchar(200) NOT NULL,
    role             source_role  NOT NULL,
    root_path        text         NOT NULL,
    include_globs    jsonb        NOT NULL DEFAULT '[]',
    exclude_globs    jsonb        NOT NULL DEFAULT '[]',
    parent_source_id uuid         REFERENCES sources (id) ON DELETE SET NULL,
    detected_stacks  jsonb        NOT NULL DEFAULT '[]',
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_sources_workspace ON sources (workspace_id);

-- ---------------- Kết quả phân tích ----------------
CREATE TABLE scan_runs (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    trigger       scan_trigger NOT NULL,
    status        scan_status  NOT NULL,
    stats         jsonb        NOT NULL DEFAULT '{}',
    error_summary text,
    started_at    timestamptz  NOT NULL,
    finished_at   timestamptz
);
CREATE INDEX idx_scan_runs_workspace ON scan_runs (workspace_id, started_at DESC);

ALTER TABLE workspaces
    ADD CONSTRAINT fk_workspaces_last_scan_run FOREIGN KEY (last_scan_run_id) REFERENCES scan_runs (id) ON DELETE SET NULL;

CREATE TABLE source_files (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id        uuid         NOT NULL REFERENCES sources (id) ON DELETE CASCADE,
    rel_path         text         NOT NULL,
    content_hash     char(64)     NOT NULL,
    language         varchar(20)  NOT NULL,
    size_bytes       integer      NOT NULL,
    parse_status     parse_status NOT NULL,
    parse_error      text,
    last_scan_run_id uuid         NOT NULL REFERENCES scan_runs (id),
    CONSTRAINT uq_source_files_path UNIQUE (source_id, rel_path)
);

CREATE TABLE ir_nodes (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    key          text        NOT NULL,
    kind         varchar(30) NOT NULL,
    source_id    uuid        NOT NULL REFERENCES sources (id) ON DELETE CASCADE,
    file_id      uuid        REFERENCES source_files (id) ON DELETE CASCADE,
    name         text        NOT NULL,
    display_name text,
    start_line   integer,
    end_line     integer,
    attrs        jsonb       NOT NULL DEFAULT '{}',
    -- Chỉ mục tìm kiếm toàn văn, tự tính từ tên/tên hiển thị/key (không cần ứng dụng cập nhật)
    search_text  tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(name, '') || ' ' || coalesce(display_name, '') || ' ' || key)
    ) STORED,
    CONSTRAINT uq_ir_nodes_key UNIQUE (workspace_id, key)
);
CREATE INDEX idx_ir_nodes_kind ON ir_nodes (workspace_id, kind);
CREATE INDEX idx_ir_nodes_search ON ir_nodes USING gin (search_text);

CREATE TABLE ir_edges (
    id           uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid            NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    from_node_id uuid            NOT NULL REFERENCES ir_nodes (id) ON DELETE CASCADE,
    to_node_id   uuid            REFERENCES ir_nodes (id) ON DELETE CASCADE,
    kind         varchar(30)     NOT NULL,
    confidence   edge_confidence NOT NULL,
    reason       text,
    call_order   integer,
    guard        jsonb,
    candidates   jsonb,
    file_id      uuid            REFERENCES source_files (id) ON DELETE CASCADE,
    line         integer
);
CREATE INDEX idx_ir_edges_from ON ir_edges (from_node_id);
CREATE INDEX idx_ir_edges_to ON ir_edges (to_node_id);

-- ---------------- Dữ liệu người dùng ----------------
CREATE TABLE link_overrides (
    id            uuid              PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid              NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    from_node_key text              NOT NULL,
    to_node_key   text,
    edge_kind     varchar(30)       NOT NULL,
    decision      override_decision NOT NULL,
    note          text,
    created_at    timestamptz       NOT NULL DEFAULT now(),
    CONSTRAINT ck_link_overrides_pin_has_target CHECK (decision <> 'PIN' OR to_node_key IS NOT NULL)
);
CREATE INDEX idx_link_overrides_workspace ON link_overrides (workspace_id);

-- ---------------- AI ----------------
CREATE TABLE ai_artifacts (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id   uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    target_key     text        NOT NULL,
    purpose        ai_purpose  NOT NULL,
    input_hash     char(64)    NOT NULL,
    prompt_version varchar(20) NOT NULL,
    provider       varchar(50) NOT NULL,
    model          varchar(50) NOT NULL,
    content        jsonb       NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_artifacts_input UNIQUE (workspace_id, purpose, target_key, input_hash)
);

CREATE TABLE llm_call_logs (
    id            uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid            REFERENCES workspaces (id) ON DELETE SET NULL,
    purpose       varchar(30)     NOT NULL,
    provider      varchar(50)     NOT NULL,
    model         varchar(50)     NOT NULL,
    prompt_hash   char(64)        NOT NULL,
    input_tokens  integer,
    output_tokens integer,
    latency_ms    integer,
    status        llm_call_status NOT NULL,
    created_at    timestamptz     NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_call_logs_created ON llm_call_logs (created_at);

CREATE TABLE app_settings (
    key        varchar(100) PRIMARY KEY,
    value      jsonb        NOT NULL,
    updated_at timestamptz  NOT NULL DEFAULT now()
);

-- ---------------- Nhóm chức năng (mở rộng — tạo sẵn từ MVP) ----------------
CREATE TABLE features (
    id           uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid           NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         varchar(200)   NOT NULL,
    description  text,
    origin       feature_origin NOT NULL,
    created_at   timestamptz    NOT NULL DEFAULT now(),
    updated_at   timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT uq_features_name UNIQUE (workspace_id, name)
);

CREATE TABLE feature_members (
    feature_id  uuid    NOT NULL REFERENCES features (id) ON DELETE CASCADE,
    node_key    text    NOT NULL,
    order_index integer NOT NULL,
    PRIMARY KEY (feature_id, node_key)
);
