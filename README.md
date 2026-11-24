# KnowledgeAI

KnowledgeAI is a local Java 21 and Spring Boot API for indexing and discovering private text sources. It uses Spring AI's embedding interface, a deterministic local model and PostgreSQL 17 with pgvector. It supports upload, retrieval, deletion and extractive answers with citations.

## Run the demo

Install Java 21, Maven 3.6.3+ and Docker Compose. PostgreSQL listens on `127.0.0.1:5440` and the API listens on `127.0.0.1:8082`. Generate the ignored `.env` once and keep it with the disposable database volume. This demo requires no external API keys.

```powershell
if (!(Test-Path -LiteralPath .env)) {
    @(
        "KNOWLEDGEAI_DB_PASSWORD=$([guid]::NewGuid().ToString('N'))"
        "KNOWLEDGEAI_ALICE_PASSWORD=$([guid]::NewGuid().ToString('N'))"
        "KNOWLEDGEAI_ALEX_PASSWORD=$([guid]::NewGuid().ToString('N'))"
        "KNOWLEDGEAI_BOB_PASSWORD=$([guid]::NewGuid().ToString('N'))"
    ) | Set-Content -LiteralPath .env -Encoding ascii
}
(ConvertFrom-StringData (Get-Content -LiteralPath .env -Raw)).GetEnumerator() | ForEach-Object {
    [Environment]::SetEnvironmentVariable($_.Key, $_.Value)
}
docker compose up -d --wait
mvn spring-boot:run '-Dspring-boot.run.profiles=demo'
```

In a second PowerShell window, load `.env` with the same `ConvertFrom-StringData` block and try the API:

```powershell
$alice = "alice:$env:KNOWLEDGEAI_ALICE_PASSWORD"
$bob = "bob:$env:KNOWLEDGEAI_BOB_PASSWORD"
$doc = curl.exe -fsS --user $alice -H "Content-Type: text/plain; charset=UTF-8" -H "X-Document-Title: Runbook" --data-binary "Kafka consumer recovery requires two operators." http://127.0.0.1:8082/api/documents | ConvertFrom-Json
curl.exe -fsS --user $alice 'http://127.0.0.1:8082/api/search?q=kafka&limit=5'
curl.exe -fsS --user $alice -G --data-urlencode 'q=What does Kafka recovery require?' http://127.0.0.1:8082/api/answers
curl.exe -fsS --user $bob -G --data-urlencode 'q=What does Kafka recovery require?' http://127.0.0.1:8082/api/answers
curl.exe -fsS --user $bob 'http://127.0.0.1:8082/api/search?q=kafka&limit=5'
curl.exe -fsS --user $alice -X DELETE "http://127.0.0.1:8082/api/documents/$($doc.id)"
```

Alice's search returns a document ID, title, chunk number, excerpt and similarity score. Bob's search returns no sources in a fresh database. Alex shares Alice's demo tenant and still cannot retrieve or delete her documents. Stop the application with Ctrl+C and run `docker compose down` to stop the database.

## API

- `POST /api/documents` accepts UTF-8 `text/plain` up to 16 KiB and an `X-Document-Title` header. It returns `201` with the indexed document ID and chunk count.
- `GET /api/search?q=...&limit=5` accepts queries up to 256 characters and returns up to ten authorized sources.
- `GET /api/answers?q=...` accepts the same question length and returns up to three quoted passages. Each `citation` maps to a source `number` with the actual document ID, title and chunk. When no authorized passage matches the question terms, the response is `{"status":"NO_SOURCES","answer":[],"sources":[]}`.
- `DELETE /api/documents/{id}` returns `204` for an owned document. A missing or inaccessible document returns `404`.

[ADR 001](docs/adr-001-local-retrieval.md) explains the authorization boundary, transaction and retrieval tradeoffs.

## Request limits

Each authenticated user has a one-minute window for uploads and a separate window shared by search and answers. Defaults are 10 uploads and 60 retrievals per minute. Set `KNOWLEDGEAI_UPLOADS_PER_MINUTE` and `KNOWLEDGEAI_RETRIEVALS_PER_MINUTE` to positive integers to override them. A request over its limit returns `429 rate_limited` with a `Retry-After` header in seconds. These budgets are local to one running process and reset on restart.

Database connections have a two-second pool wait, and PostgreSQL statements use a two-second timeout. A canceled statement returns `503 database_timeout`. The offline provider makes no billable requests.

## Tests

Run `mvn -B --no-transfer-progress verify`. The integration suite uses MockMvc with disposable PostgreSQL and pgvector in Testcontainers. It covers owner and tenant isolation before ranking, deletion of chunks, anonymous requests, malformed or oversized input and source attribution when documents contain instruction-like text.

## Limits

The local model uses hashed word counts, so vocabulary overlap and hash collisions affect candidate ranking. Answers quote existing text with exact term overlap and use server-assigned references. Common question words do not count as evidence. A question with multiple substantive terms must match at least two terms in the same sentence. The provider treats document text as data and performs no actions. The API is loopback-only and the demo profile uses HTTP Basic authentication with synthetic accounts. External providers, generative chat, document sharing, distributed rate limiting and production deployment are future work.
