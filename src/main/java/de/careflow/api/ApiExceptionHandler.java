package de.careflow.api;

import de.careflow.cds.CdsBlockException;
import de.careflow.cds.CdsEngine;
import de.careflow.domain.IllegalOrderStateException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CdsBlockException.class)
    public ResponseEntity<Map<String, Object>> blocked(CdsBlockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "CDS_BLOCK",
                "message", ex.getMessage(),
                "alerts", ex.getFindings().stream().map(ApiExceptionHandler::alert).toList()));
    }

    @ExceptionHandler(IllegalOrderStateException.class)
    public ResponseEntity<Map<String, String>> illegal(IllegalOrderStateException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "ILLEGAL_STATE", "message", ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> concurrent(OptimisticLockingFailureException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "OPTIMISTIC_LOCK", "message", "Auftrag wurde parallel geändert, bitte neu laden"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidBody(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldError();
        return validation(first != null ? germanField(first) : "Eingabe ungültig");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, String>> invalidMethod(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
                .map(ApiExceptionHandler::germanError)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Eingabe ungültig");
        return validation(message);
    }

    private static Map<String, String> alert(CdsEngine.Finding finding) {
        return Map.of(
                "ruleId", finding.ruleId(),
                "severity", finding.severity(),
                "title", finding.title(),
                "message", finding.message());
    }

    private static ResponseEntity<Map<String, String>> validation(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "VALIDATION", "message", message));
    }

    private static String germanError(MessageSourceResolvable error) {
        if (error instanceof FieldError fieldError) {
            return germanField(fieldError);
        }
        return germanize(error.getDefaultMessage());
    }

    private static String germanField(FieldError error) {
        return error.getField() + " " + germanize(error.getDefaultMessage());
    }

    private static String germanize(String defaultMessage) {
        if (defaultMessage == null || defaultMessage.isBlank()) {
            return "darf nicht leer sein";
        }
        String lower = defaultMessage.toLowerCase(Locale.ROOT);
        if (lower.startsWith("must ") || lower.contains("must not")) {
            return "darf nicht leer sein";
        }
        return defaultMessage;
    }
}
