package io.github.lucianolattante.knowledgeai.documents;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.github.lucianolattante.knowledgeai.documents.DocumentService.Identity;
import io.github.lucianolattante.knowledgeai.documents.DocumentService.IndexedChunk;
import io.github.lucianolattante.knowledgeai.documents.DocumentService.Source;

@Repository
class DocumentStore {
    private final JdbcClient jdbc;

    DocumentStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertDocument(Identity owner, UUID id, String title) {
        jdbc.sql("INSERT INTO document (id, tenant, owner_name, title) VALUES (:id, :tenant, :owner, :title)")
                .param("id", id).param("tenant", owner.tenant()).param("owner", owner.username())
                .param("title", title).update();
    }

    void insertChunks(UUID documentId, List<IndexedChunk> chunks) {
        for (IndexedChunk chunk : chunks) {
            jdbc.sql("""
                    INSERT INTO document_chunk (document_id, ordinal, content, embedding)
                    VALUES (:document, :ordinal, :content, CAST(:vector AS vector))
                    """)
                    .param("document", documentId).param("ordinal", chunk.index())
                    .param("content", chunk.text()).param("vector", chunk.vector()).update();
        }
    }

    List<OwnedSource> search(Identity identity, String vector, int limit) {
        return jdbc.sql("""
                SELECT d.id, d.tenant, d.owner_name, d.title, c.ordinal, c.content,
                       1 - (c.embedding <=> CAST(:vector AS vector)) AS score
                FROM document d JOIN document_chunk c ON c.document_id = d.id
                WHERE d.tenant = :tenant AND d.owner_name = :owner
                  AND (c.embedding <=> CAST(:vector AS vector)) < 1
                ORDER BY score DESC, d.id, c.ordinal
                LIMIT :limit
                """)
                .param("tenant", identity.tenant()).param("owner", identity.username())
                .param("vector", vector).param("limit", limit)
                .query((rs, row) -> new OwnedSource(
                        new Identity(rs.getString("tenant"), rs.getString("owner_name")),
                        new Source(rs.getObject("id", UUID.class), rs.getString("title"),
                                rs.getInt("ordinal"), rs.getString("content"), rs.getDouble("score"))))
                .list();
    }

    int delete(Identity identity, UUID id) {
        return jdbc.sql("DELETE FROM document WHERE id = :id AND tenant = :tenant AND owner_name = :owner")
                .param("id", id).param("tenant", identity.tenant()).param("owner", identity.username()).update();
    }

    record OwnedSource(Identity identity, Source source) {}
}
