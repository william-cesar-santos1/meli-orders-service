-- Tabela de outbox para o padrao Outbox (Bloco 3)
-- Garante consistencia eventual entre PostgreSQL e Elasticsearch
-- sem dual write: o evento so e publicado apos o commit no Postgres
CREATE TABLE order_outbox
(
    id           UUID PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    processed_at TIMESTAMP    NULL
);
CREATE INDEX idx_outbox_unprocessed ON order_outbox (created_at)
    WHERE processed_at IS NULL;
