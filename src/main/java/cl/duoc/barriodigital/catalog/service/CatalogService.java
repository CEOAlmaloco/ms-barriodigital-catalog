package cl.duoc.barriodigital.catalog.service;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeSeed;
import cl.duoc.barriodigital.catalog.web.AdminCaller;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lee los tipos en cada llamada. No guarda la lista en memoria:
 * un alta hecha después aparece en el siguiente GET sin reiniciar.
 */
@Service
public class CatalogService {

    private static final Pattern VALUE_PATTERN = Pattern.compile("^[a-z0-9_-]{1,40}$");

    private final ProcedureTypeRepository repository;
    private final EntityManager entityManager;

    public CatalogService(ProcedureTypeRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    public List<ProcedureView> listProcedures() {
        return repository.findAllByOrderByValueAsc().stream()
                .map(ProcedureView::from)
                .toList();
    }

    @Transactional
    public ProcedureView create(CreateProcedureRequest body, String rolesHeader) {
        if (!AdminCaller.isAdmin(rolesHeader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Admin puede crear tipos de tramite");
        }
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El codigo value es obligatorio");
        }

        String value = requireValue(body.value());
        String label = requireLabel(body.label());
        boolean active = body.active() == null || body.active();

        if (repository.existsById(value)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El codigo value ya existe");
        }

        ProcedureType type = new ProcedureType(
                value,
                label,
                active,
                ProcedureTypeSeed.INITIAL_DAILY_QUOTA,
                LocalDate.now(ZoneOffset.UTC)
        );
        insertOnly(type);
        return ProcedureView.from(type);
    }

    /**
     * Inserta y fuerza el flush en esta transacción.
     * persist, no merge: un value repetido choca con la clave y no pisa la fila.
     */
    @Transactional
    public void insertOnly(ProcedureType type) {
        try {
            entityManager.persist(type);
            entityManager.flush();
        } catch (RuntimeException ex) {
            if (isDuplicateKey(ex)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El codigo value ya existe");
            }
            throw ex;
        }
    }

    /** Solo la clave duplicada (ORA-00001 o SQLState 23505). El resto de errores de integridad sigue su curso. */
    private static boolean isDuplicateKey(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DuplicateKeyException) {
                return true;
            }
            if (current instanceof SQLException sql && isUniqueViolation(sql)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isUniqueViolation(SQLException sql) {
        SQLException current = sql;
        while (current != null) {
            if ("23505".equals(current.getSQLState()) || current.getErrorCode() == 1) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    private static String requireValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El codigo value es obligatorio");
        }
        String value = raw.trim();
        if (!VALUE_PATTERN.matcher(value).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El codigo value debe ser minusculas, numeros, guion o guion bajo"
            );
        }
        return value;
    }

    private static String requireLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La etiqueta es obligatoria");
        }
        String label = raw.trim();
        if (label.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La etiqueta supera los 120 caracteres");
        }
        return label;
    }
}
