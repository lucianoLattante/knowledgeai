# ADR 001 - Retrieve authorized local document sources

## Context

The first increment must run without network calls to AI providers and return only the caller's sources. A demo needs repeatable inputs and results.

## Decision

Store document ownership in PostgreSQL and derive the tenant and owner from the authenticated identity. Apply both constraints in the SQL query before ranking and verify identity again when mapping results. Return only source fields. Accept bounded UTF-8 text and split it at whitespace when possible without splitting Unicode code points. Embed and index chunks in the same transaction as the document row. Delete the owned document and its chunks through a cascading foreign key.

Use a deterministic Spring AI embedding model and exact cosine ranking through pgvector. Use a composite relational index to locate the authorized corpus. Return the document ID and chunk ordinal so callers can identify the source.

Apply the same retrieval boundary to answers. Use only authorized excerpts with exact query-term overlap, then select the strongest matching sentence from each excerpt. Quote up to three passages and assign citation numbers from trusted source metadata. Retrieved text is never interpreted as an instruction, and the offline provider cannot perform actions.

## Consequences

Ranking is reproducible without credentials or external calls. Hashed vocabulary does not model semantic relationships, and exact search cost grows with the authorized corpus. Extractive answers can miss relevant material when wording differs. An approximate vector index or a hosted model would introduce ranking and operational tradeoffs. Shared access, production identity and generated answers are separate future increments.
