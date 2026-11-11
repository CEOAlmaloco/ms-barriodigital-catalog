package cl.duoc.barriodigital.catalog.messaging;

import cl.duoc.barriodigital.catalog.persistence.OutboxEvent;
import cl.duoc.barriodigital.catalog.persistence.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;

/**
 * Registra el evento de auditoría en outbox_events dentro de la misma transacción del cambio.
 * CatalogService no conoce Kafka.
 */
@Component
public class AuditOutboxRecorder {

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditOutboxRecorder(OutboxEventRepository outbox, ObjectMapper objectMapper, Clock clock) {
        this.outbox = outbox;
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.clock = clock;
    }

    /** Persiste el sobre completo (sin sequence) para publicación diferida. */
    public void record(AuditEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo serializar el evento de auditoria");
        }
        outbox.save(new OutboxEvent(
                envelope.eventId().toString(),
                envelope.key(),
                envelope.eventType(),
                json,
                Instant.now(clock)
        ));
    }
}
