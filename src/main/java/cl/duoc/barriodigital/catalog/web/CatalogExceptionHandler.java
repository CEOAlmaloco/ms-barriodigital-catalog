package cl.duoc.barriodigital.catalog.web;

import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
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
 * Un fallo de base de datos no sale con SQL ni con el código de Oracle: queda en el log.
 */
@RestControllerAdvice
public class CatalogExceptionHandler {

    static final String PERSISTENCE_MESSAGE = "No se pudieron guardar los cambios. Inténtalo nuevamente.";

    private static final Logger log = LoggerFactory.getLogger(CatalogExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        return ResponseEntity.status(status).body(body(status.value(), ex.getReason() == null ? "" : ex.getReason()));
    }

    @ExceptionHandler({DataAccessException.class, PersistenceException.class})
    public ResponseEntity<Map<String, Object>> persistence(RuntimeException ex) {
        log.error("Error de persistencia en catalog", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR.value(), PERSISTENCE_MESSAGE));
    }

    private static Map<String, Object> body(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", errorName(status));
        body.put("message", message);
        return body;
    }

    private static String errorName(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 500 -> "Internal Server Error";
            default -> "Error";
        };
    }
}
