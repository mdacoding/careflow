package de.careflow.api;

import de.careflow.cds.CdsBlockException;
import de.careflow.cds.CdsEngine;
import de.careflow.domain.IllegalOrderStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

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

    private static Map<String, String> alert(CdsEngine.Finding finding) {
        return Map.of(
                "ruleId", finding.ruleId(),
                "severity", finding.severity(),
                "title", finding.title(),
                "message", finding.message());
    }
}
