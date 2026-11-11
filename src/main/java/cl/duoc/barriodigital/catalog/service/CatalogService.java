package cl.duoc.barriodigital.catalog.service;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import cl.duoc.barriodigital.catalog.messaging.AuditEventFactory;
import cl.duoc.barriodigital.catalog.messaging.AuditOutboxRecorder;
import cl.duoc.barriodigital.catalog.messaging.ProcedureTypeAuditState;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
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
 * Aplica las reglas del catálogo leyendo Oracle en cada llamada.
 * Un tipo creado aparece en la consulta siguiente, sin reiniciar el servicio.
 * Alta y edición registran el evento en el outbox en la misma transacción; reserve/release no.
 */
@Service
public class CatalogService {

    private static final Pattern VALUE_PATTERN = Pattern.compile("^[a-z0-9_-]{1,40}$");
    public static final String SLA_RANGE_MESSAGE = "El plazo debe ser un entero entre 1 y 120 dias habiles";
    public static final int SLA_MIN = 1;
    public static final int SLA_MAX = 120;
    private static final String RESOURCE_TYPE = "procedure-type";

    private final ProcedureTypeRepository repository;
    private final EntityManager entityManager;
    private final Clock clock;
    private final AuditEventFactory auditEvents;
    private final AuditOutboxRecorder auditOutbox;

    /** Recibe persistencia, reloj de Chile y el registrador de outbox de auditoría. */
    public CatalogService(
            ProcedureTypeRepository repository,
            EntityManager entityManager,
            Clock clock,
            AuditEventFactory auditEvents,
            AuditOutboxRecorder auditOutbox
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.clock = clock;
        this.auditEvents = auditEvents;
        this.auditOutbox = auditOutbox;
    }

    /**
     * Lista los tipos ordenados por código.
     * Si cambió el día en hora de Chile, repone el cupo antes de devolverlos.
     */
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

    /**
     * Crea un tipo y exige rol Admin según el encabezado que envía el BFF.
     * El cupo queda en null, sin límite. slaBusinessDays es opcional; null deja el tipo sin plazo.
     * Si el código ya existe, responde 409.
     */
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
        Integer slaBusinessDays = optionalSlaBusinessDays(body.slaBusinessDays());

        if (repository.existsById(value)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El codigo value ya existe");
        }

        ProcedureType type = new ProcedureType(value, label, active, null, slaBusinessDays, today());
        insertOnly(type);
        auditOutbox.record(auditEvents.create(
                RESOURCE_TYPE,
                value,
                "created",
                null,
                ProcedureTypeAuditState.from(type)
        ));
        return ProcedureView.from(type);
    }

    /**
     * Inserta la fila y hace flush para que la base confirme en esta transacción.
     * Si el código ya existe, no sobrescribe la fila anterior y responde 409.
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

    /** Indica si el error es una clave duplicada. El resto de fallos se propaga. */
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

    /** Reconoce clave única repetida: SQLState 23505 o código Oracle 1. */
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

    /**
     * Edita etiqueta, estado activo, cupo y plazo; solo Admin, y el código no cambia.
     * dailyQuota o slaBusinessDays null quitan el límite o el plazo.
     */
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
        Integer dailyQuota = optionalDailyQuota(body.dailyQuota());
        Integer slaBusinessDays = optionalSlaBusinessDays(body.slaBusinessDays());

        ProcedureType current = lock(value);
        current.resetForToday(today());
        ProcedureTypeAuditState before = ProcedureTypeAuditState.from(current);
        current.updateDetails(label, body.active(), dailyQuota, slaBusinessDays);
        ProcedureType saved = repository.save(current);
        auditOutbox.record(auditEvents.create(
                RESOURCE_TYPE,
                value,
                "updated",
                before,
                ProcedureTypeAuditState.from(saved)
        ));
        return ProcedureView.from(saved);
    }

    /**
     * Descuenta un cupo al admitir: primero repone el día en hora de Chile y después resta uno; sin límite no descuenta.
     * No valida rol porque solo lo llama el BFF. El bloqueo de la fila evita dos descuentos a la vez.
     */
    @Transactional
    public ProcedureView reserve(String pathValue) {
        ProcedureType current = lock(pathValue);
        current.resetForToday(today());
        if (!current.reserveOne()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No queda cupo para este tipo de tramite");
        }
        return ProcedureView.from(repository.save(current));
    }

    /**
     * Devuelve un cupo si la admisión no se guardó: suma uno sin pasar del máximo del día y, sin límite, no cambia el contador.
     * No valida rol porque solo lo llama el BFF.
     */
    @Transactional
    public ProcedureView release(String pathValue) {
        ProcedureType current = lock(pathValue);
        current.releaseOne();
        return ProcedureView.from(repository.save(current));
    }

    /** Busca el tipo y lo bloquea para que otra reserva o devolución no lo cambie a la vez. */
    private ProcedureType lock(String pathValue) {
        String value = pathValue == null ? "" : pathValue.trim();
        return repository.findByValueForUpdate(value)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El tipo de tramite no existe"));
    }

    /** Fecha de hoy según el reloj de Chile. El reset del cupo usa este día. */
    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Exige un código de hasta 40 caracteres: minúsculas, números, guion o guion bajo. */
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

    /** Exige una etiqueta no vacía de hasta 120 caracteres. */
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

    /** Null significa sin límite. Si viene un número, exige que sea al menos 1. */
    private static Integer optionalDailyQuota(Integer dailyQuota) {
        if (dailyQuota == null) {
            return null;
        }
        if (dailyQuota < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cupo diario debe ser al menos 1");
        }
        return dailyQuota;
    }

    /** Null significa sin plazo. Si viene un número, exige el rango de EP3-06: 1 a 120. */
    private static Integer optionalSlaBusinessDays(Integer slaBusinessDays) {
        if (slaBusinessDays == null) {
            return null;
        }
        if (slaBusinessDays < SLA_MIN || slaBusinessDays > SLA_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, SLA_RANGE_MESSAGE);
        }
        return slaBusinessDays;
    }

    /** Rechaza el formulario si trae un código distinto al de la URL. El código no se cambia. */
    private static void rejectValueChange(String pathValue, String bodyValue) {
        if (bodyValue == null || bodyValue.isBlank()) {
            return;
        }
        if (!pathValue.equals(bodyValue.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede cambiar el codigo value");
        }
    }
}
