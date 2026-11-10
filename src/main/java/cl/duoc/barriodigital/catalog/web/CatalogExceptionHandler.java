package cl.duoc.barriodigital.catalog.web;

import cl.duoc.barriodigital.catalog.service.CatalogService;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
    public static final String BODY_INVALID = "El cuerpo de la solicitud no es valido";
    public static final String DAILY_QUOTA_INTEGER = "El cupo diario debe ser un entero";

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

    /**
     * Body ilegible: 403 si no es Admin (POST/PUT), o 400 con el mensaje del campo.
     * Conserva el orden 403, 400, 404 de catalog.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String roles = request.getHeader(AdminCaller.HEADER_USER_ROLES);
        if (!AdminCaller.isAdmin(roles)) {
            String method = request.getMethod() == null ? "" : request.getMethod();
            String reason = "PUT".equalsIgnoreCase(method)
                    ? "Solo Admin puede editar tipos de tramite"
                    : "Solo Admin puede crear tipos de tramite";
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(403, reason));
        }
        return ResponseEntity.badRequest().body(errorBody(400, messageForUnreadable(ex)));
    }

    /** Traduce el código HTTP a su frase estándar, por ejemplo Not Found. */
    private static String errorName(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved == null ? "Error" : resolved.getReasonPhrase();
    }

    private static Map<String, Object> errorBody(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", errorName(status));
        body.put("message", message);
        return body;
    }

    private static String messageForUnreadable(HttpMessageNotReadableException ex) {
        MismatchedInputException mismatched = findMismatched(ex);
        if (mismatched == null || mismatched.getPath() == null || mismatched.getPath().isEmpty()) {
            return BODY_INVALID;
        }
        String field = mismatched.getPath().get(mismatched.getPath().size() - 1).getFieldName();
        if ("slaBusinessDays".equals(field)) {
            return CatalogService.SLA_RANGE_MESSAGE;
        }
        if ("dailyQuota".equals(field)) {
            return DAILY_QUOTA_INTEGER;
        }
        return BODY_INVALID;
    }

    private static MismatchedInputException findMismatched(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof MismatchedInputException mismatched) {
                return mismatched;
            }
            current = current.getCause();
        }
        return null;
    }
}
