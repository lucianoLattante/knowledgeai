CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document (
    id UUID PRIMARY KEY,
    tenant TEXT NOT NULL,
    owner_name TEXT NOT NULL,
    title VARCHAR(120) NOT NULL
);

CREATE INDEX document_tenant_owner ON document (tenant, owner_name);

CREATE TABLE document_chunk (
    document_id UUID NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 1),
    content TEXT NOT NULL,
    embedding vector(256) NOT NULL,
    PRIMARY KEY (document_id, ordinal)
);
