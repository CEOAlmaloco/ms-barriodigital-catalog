package cl.duoc.barriodigital.catalog.web;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * El cuerpo de error de Spring deja {@code message} vacío si no se rellena a mano.
 * El contrato de catálogo necesita ese texto en 400, 403, 404 y 409.
 */
@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", errorName(status.value()));
        body.put("message", ex.getReason() == null ? "" : ex.getReason());
        return ResponseEntity.status(status).body(body);
    }

    private static String errorName(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            default -> "Error";
        };
    }
}
