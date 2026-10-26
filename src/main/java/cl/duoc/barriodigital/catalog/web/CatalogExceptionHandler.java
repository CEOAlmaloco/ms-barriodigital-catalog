package cl.duoc.barriodigital.catalog.web;

import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escribe los errores del catálogo en JSON con status, error y message.
 * Si Oracle o Hibernate fallan, el detalle queda en el log y el cliente recibe un mensaje genérico.
 */
@RestControllerAdvice
public class CatalogExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CatalogExceptionHandler.class);
    public static final String INTERNAL_ERROR = "No pudimos procesar la solicitud. Intenta nuevamente.";

    /**
     * Responde 500 ante un fallo de persistencia, sin incluir la excepción interna.
     * Así no se filtran SQL ni trazas al cliente.
     */
    @ExceptionHandler({DataAccessException.class, TransactionException.class, PersistenceException.class})
    public ResponseEntity<Map<String, Object>> persistence(RuntimeException ex) {
        // El detalle de Oracle/Hibernate queda en el servidor, nunca en la respuesta HTTP.
        log.error("Error de persistencia en catalogo", ex);
        return ResponseEntity.internalServerError().body(Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "message", INTERNAL_ERROR
        ));
    }

    /**
     * Copia al JSON el mensaje de un error esperado, como 400, 403, 404 o 409.
     * En 5xx sustituye el detalle por el mensaje genérico.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.is5xxServerError()) {
            log.error("Error interno en catalogo", ex);
            return ResponseEntity.status(status).body(Map.of(
                    "status", status.value(),
                    "error", errorName(status.value()),
                    "message", INTERNAL_ERROR
            ));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", errorName(status.value()));
        body.put("message", ex.getReason() == null ? "" : ex.getReason());
        return ResponseEntity.status(status).body(body);
    }

    /** Traduce el código HTTP a su frase estándar, por ejemplo Not Found. */
    private static String errorName(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved == null ? "Error" : resolved.getReasonPhrase();
    }
}
