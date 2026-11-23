package io.github.lucianolattante.knowledgeai;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "knowledgeai.demo.alice-password=alice-integration-fixture",
        "knowledgeai.demo.alex-password=alex-integration-fixture",
        "knowledgeai.demo.bob-password=bob-integration-fixture"
})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@Testcontainers
class KnowledgeAiIT {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;

    @Test
    void returnsOnlyOwnedSourcesEvenWhenOtherUsersRankHigher() throws Exception {
        String term = "isolationalphaprobe";
        UUID owned = upload("alice", "Owned", term + " guidance");
        upload("alex", "Other owner", term);
        upload("bob", "Other tenant", term);

        var results = searchAs("alice", term, 1);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("documentId").asText()).isEqualTo(owned.toString());
        assertThat(results.get(0).get("title").asText()).isEqualTo("Owned");
        assertThat(results.get(0).get("excerpt").asText()).contains(term);
        assertThat(results.get(0).get("chunk").asInt()).isEqualTo(1);
    }

    @Test
    void onlyOwnerCanDeleteAndDeletionRemovesEveryChunk() throws Exception {
        String term = "purge" + UUID.randomUUID().toString().replace("-", "");
        var id = upload("alice", "Delete me", (term + " ").repeat(40));
        assertThat(chunks(id)).isGreaterThan(1);
        assertThat(status(http.perform(delete("/api/documents/{id}", id)
                .header("Authorization", basic("alex"))).andReturn())).isEqualTo(404);
        assertThat(status(http.perform(delete("/api/documents/{id}", id)
                .header("Authorization", basic("bob"))).andReturn())).isEqualTo(404);
        assertThat(chunks(id)).isGreaterThan(1);
        assertThat(status(http.perform(delete("/api/documents/{id}", id)
                .header("Authorization", basic("alice"))).andReturn())).isEqualTo(204);
        assertThat(chunks(id)).isZero();
        for (JsonNode hit : searchAs("alice", term, 10)) {
            assertThat(hit.get("documentId").asText()).isNotEqualTo(id.toString());
        }
    }

    @Test
    void rejectsOversizedInvalidAndUnsupportedRequests() throws Exception {
        assertThat(status(uploadBytes("alice", "Maximum", "x".repeat(16_384).getBytes(StandardCharsets.UTF_8))))
                .isEqualTo(201);
        assertProblem(uploadBytes("alice", "Too large", "é".repeat(8_193).getBytes(StandardCharsets.UTF_8)),
                413, "document_too_large");
        assertProblem(uploadBytes("alice", "Invalid", new byte[]{(byte) 0xC3, 0x28}), 400, "invalid_utf8");
        assertProblem(uploadBytes("alice", "Blank", " ".getBytes(StandardCharsets.UTF_8)), 400, "empty_text");
        assertThat(status(http.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Authorization", basic("alice")).header("X-Document-Title", "Unsupported"))
                .andReturn())).isEqualTo(415);
        assertProblem(http.perform(get("/api/search").param("q", "term").param("limit", "11")
                .header("Authorization", basic("alice"))).andReturn(), 400, "invalid_query");
    }

    @Test
    void rejectsAnonymousSearchAndIgnoresCallerSuppliedTenant() throws Exception {
        String term = "private" + UUID.randomUUID().toString().replace("-", "");
        UUID id = upload("bob", "Private", term);
        assertThat(status(http.perform(get("/api/search").param("q", term)).andReturn())).isEqualTo(401);
        var spoofed = http.perform(get("/api/search").param("q", term).param("tenant", "beta")
                .header("Authorization", basic("alice")).header("X-Tenant", "beta")).andReturn();
        assertThat(status(spoofed)).isEqualTo(200);
        for (JsonNode hit : body(spoofed)) {
            assertThat(hit.get("documentId").asText()).isNotEqualTo(id.toString());
        }
    }

    private UUID upload(String actor, String title, String content) throws Exception {
        var result = uploadBytes(actor, title, content.getBytes(StandardCharsets.UTF_8));
        assertThat(status(result)).isEqualTo(201);
        return UUID.fromString(body(result).get("id").asText());
    }

    private MvcResult uploadBytes(String actor, String title, byte[] bytes) throws Exception {
        return http.perform(post("/api/documents").contentType(MediaType.TEXT_PLAIN).content(bytes)
                .header("Authorization", basic(actor)).header("X-Document-Title", title)).andReturn();
    }

    private JsonNode searchAs(String actor, String term, int limit) throws Exception {
        var result = http.perform(get("/api/search").param("q", term).param("limit", Integer.toString(limit))
                .header("Authorization", basic(actor))).andReturn();
        assertThat(status(result)).isEqualTo(200);
        return body(result);
    }

    private long chunks(UUID document) {
        return jdbc.sql("SELECT count(*) FROM document_chunk WHERE document_id = :id")
                .param("id", document).query(Long.class).single();
    }

    private void assertProblem(MvcResult result, int expectedStatus, String code) throws Exception {
        assertThat(status(result)).isEqualTo(expectedStatus);
        assertThat(body(result).get("code").asText()).isEqualTo(code);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private static String basic(String actor) {
        var raw = (actor + ":" + actor + "-integration-fixture").getBytes(StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(raw);
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }
}
