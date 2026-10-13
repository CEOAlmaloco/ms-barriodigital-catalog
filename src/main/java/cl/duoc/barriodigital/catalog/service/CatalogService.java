package cl.duoc.barriodigital.catalog.service;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeSeed;
import cl.duoc.barriodigital.catalog.web.AdminCaller;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import cl.duoc.barriodigital.catalog.web.dto.UpdateProcedureRequest;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
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
    private final Clock clock;

    public CatalogService(ProcedureTypeRepository repository, EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public List<ProcedureView> listProcedures() {
        LocalDate today = today();
        return repository.findAllByOrderByValueAsc().stream()
                .map(type -> {
                    if (type.resetForToday(today)) {
                        repository.save(type);
                    }
                    return ProcedureView.from(type);
                })
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
                today()
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

    @Transactional
    public ProcedureView update(String pathValue, UpdateProcedureRequest body, String rolesHeader) {
        if (!AdminCaller.isAdmin(rolesHeader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Admin puede editar tipos de tramite");
        }
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La etiqueta es obligatoria");
        }

        String value = pathValue == null ? "" : pathValue.trim();
        rejectValueChange(value, body.value());
        String label = requireLabel(body.label());
        if (body.active() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo active es obligatorio");
        }
        int dailyQuota = requireDailyQuota(body.dailyQuota());

        ProcedureType current = repository.findById(value)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El tipo de tramite no existe"));
        current.resetForToday(today());
        current.updateDetails(label, body.active(), dailyQuota);
        return ProcedureView.from(repository.save(current));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
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

    private static int requireDailyQuota(Integer dailyQuota) {
        if (dailyQuota == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cupo diario es obligatorio");
        }
        if (dailyQuota < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cupo diario debe ser al menos 1");
        }
        return dailyQuota;
    }

    private static void rejectValueChange(String pathValue, String bodyValue) {
        if (bodyValue == null || bodyValue.isBlank()) {
            return;
        }
        if (!pathValue.equals(bodyValue.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede cambiar el codigo value");
        }
    }
}
