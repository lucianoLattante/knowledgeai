# Reproducible retrieval evaluation

Run `mvn -B --no-transfer-progress verify` with Docker. The `KnowledgeAiIT` suite uploads synthetic text to a disposable PostgreSQL/pgvector instance. No hosted model or external provider is involved.

| Fixture | Expected result |
| --- | --- |
| Exact runbook wording with a competing document | One answer excerpt and a server-assigned citation to the owned runbook |
| Unrelated question sharing common function words | NO_SOURCES |
| Different vocabulary with only incidental term overlap | NO_SOURCES |
| A synthetic instruction-like sentence next to the answer | Returned excerpt quotes the appropriate sentence and keeps server-assigned citation numbering |
| Another owner in the same tenant | No citation to Alice's matching material |
| Another tenant with overlapping vocabulary | No citation to Alice's matching material |

A static list removes common question/function words in English and Spanish from answer matching. A question with multiple substantive terms must match at least two in one sentence. This reduces weak matches, at the cost of abstaining on some paraphrases and inflected forms. Ranking remains deterministic hashed-token cosine similarity. Instruction-like text is data. The service does not execute documents, call tools, or generate text. This fixture suite measures expected behavior on controlled examples and cannot establish broad retrieval quality.
