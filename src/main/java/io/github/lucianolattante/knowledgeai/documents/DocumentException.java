package io.github.lucianolattante.knowledgeai.documents;

public final class DocumentException extends RuntimeException {
    public enum Reason {
        DOCUMENT_NOT_FOUND, EMPTY_TEXT, INVALID_UTF8, INVALID_TITLE, INVALID_QUERY, PAYLOAD_TOO_LARGE,
        DOCUMENT_LIMIT_REACHED
    }

    private final Reason reason;

    public DocumentException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
