package io.github.lucianolattante.knowledgeai.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.github.lucianolattante.knowledgeai.documents.DocumentException;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(DocumentException.class)
    ProblemDetail documentError(DocumentException exception) {
        return switch (exception.reason()) {
            case DOCUMENT_NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "document_not_found", "Document unavailable.");
            case EMPTY_TEXT -> problem(HttpStatus.BAD_REQUEST, "empty_text", "Supply text with indexable words.");
            case INVALID_UTF8 -> problem(HttpStatus.BAD_REQUEST, "invalid_utf8", "Supply UTF-8 text without NUL bytes.");
            case INVALID_TITLE -> problem(HttpStatus.BAD_REQUEST, "invalid_title", "Supply a nonempty title up to 120 characters.");
            case INVALID_QUERY -> problem(HttpStatus.BAD_REQUEST, "invalid_query", "Supply a query up to 256 characters and a limit from 1 to 10.");
            case PAYLOAD_TOO_LARGE -> problem(HttpStatus.PAYLOAD_TOO_LARGE, "document_too_large", "Supply at most 16384 bytes.");
        };
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "invalid_request", "Check request fields and headers.");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setProperty("code", code);
        return result;
    }
}
