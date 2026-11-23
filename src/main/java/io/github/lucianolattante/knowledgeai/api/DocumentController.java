package io.github.lucianolattante.knowledgeai.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.lucianolattante.knowledgeai.documents.DocumentException;
import io.github.lucianolattante.knowledgeai.documents.DocumentService;
import io.github.lucianolattante.knowledgeai.documents.DocumentService.Identity;
import io.github.lucianolattante.knowledgeai.documents.DocumentService.IndexedDocument;
import io.github.lucianolattante.knowledgeai.documents.DocumentService.Source;

import static io.github.lucianolattante.knowledgeai.documents.DocumentException.Reason.*;

@RestController
@RequestMapping("/api")
class DocumentController {
    private static final int MAX_DOCUMENT_BYTES = 16_384;
    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(path = "/documents", consumes = MediaType.TEXT_PLAIN_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    IndexedDocument upload(Authentication caller, @RequestHeader("X-Document-Title") String title,
                           HttpServletRequest request) throws IOException {
        Identity owner = identity(caller);
        byte[] body = request.getInputStream().readNBytes(MAX_DOCUMENT_BYTES + 1);
        if (body.length > MAX_DOCUMENT_BYTES) {
            throw new DocumentException(PAYLOAD_TOO_LARGE);
        }
        try {
            var text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            return documents.ingest(owner, title, text);
        } catch (CharacterCodingException ex) {
            throw new DocumentException(INVALID_UTF8);
        }
    }

    @GetMapping("/search")
    List<Source> search(Authentication caller, @RequestParam String q, @RequestParam(defaultValue = "5") int limit) {
        return documents.search(identity(caller), q, limit);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication caller, @PathVariable UUID id) {
        documents.delete(identity(caller), id);
    }

    private Identity identity(Authentication caller) {
        for (var authority : caller.getAuthorities()) {
            switch (authority.getAuthority()) {
                case DemoSecurity.ALPHA -> { return new Identity("alpha", caller.getName()); }
                case DemoSecurity.BETA -> { return new Identity("beta", caller.getName()); }
                default -> { }
            }
        }
        throw new AccessDeniedException("No tenant assigned");
    }
}
