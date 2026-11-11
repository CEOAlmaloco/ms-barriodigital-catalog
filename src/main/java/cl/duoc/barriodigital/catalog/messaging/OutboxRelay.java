package cl.duoc.barriodigital.catalog.messaging;

import cl.duoc.barriodigital.catalog.persistence.OutboxEvent;
import cl.duoc.barriodigital.catalog.persistence.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publica pendientes de outbox_events en audit.timeline (patrón relay de requests / EP3-02).
 * Orden por event_sequence; ante falla detiene el lote. No agrega sequence al JSON.
 */
@Component
public class OutboxRelay {

    public static final String TOPIC = "audit.timeline";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final AuditKafkaSender sender;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final AtomicBoolean halted = new AtomicBoolean(false);

    public OutboxRelay(
            OutboxEventRepository outbox,
            AuditKafkaSender sender,
            Clock clock,
            TransactionTemplate tx
    ) {
        this.outbox = outbox;
        this.sender = sender;
        this.clock = clock;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${barriodigital.outbox.relay-delay-ms:1000}")
    public void publishPending() {
        List<OutboxEvent> pending = tx.execute(status ->
                outbox.findFirst100ByPublishedAtIsNullOrderByEventSequenceAscCreatedAtAsc());
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (OutboxEvent event : pending) {
            try {
                sender.sendConfirmed(TOPIC, event.getAggregateId(), event.getPayload());
                tx.executeWithoutResult(status -> markPublished(event.getEventId()));
                if (halted.compareAndSet(true, false)) {
                    log.info("relay-reanudado pendientes={}", outbox.countByPublishedAtIsNull());
                }
            } catch (Exception e) {
                String motivo = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                tx.executeWithoutResult(status -> markFailed(event.getEventId(), motivo));
                if (halted.compareAndSet(false, true)) {
                    log.error(
                            "relay-detenido sequence={} eventId={} motivo=\"{}\"",
                            event.getEventSequence(),
                            event.getEventId(),
                            motivo
                    );
                }
                return;
            }
        }
    }

    private void markPublished(String eventId) {
        OutboxEvent event = outbox.findById(eventId).orElse(null);
        if (event == null || event.getPublishedAt() != null) {
            return;
        }
        event.markPublished(Instant.now(clock));
        outbox.save(event);
    }

    private void markFailed(String eventId, String motivo) {
        OutboxEvent event = outbox.findById(eventId).orElse(null);
        if (event == null || event.getPublishedAt() != null) {
            return;
        }
        event.markFailed(motivo);
        outbox.save(event);
    }
}
