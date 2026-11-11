package cl.duoc.barriodigital.catalog.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * Fila del outbox de auditoría (mismo patrón que requests / EP3-02).
 * event_sequence lo asigna la base; el JSON de audit.timeline no lleva sequence.
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_pending", columnList = "published_at, event_sequence")
})
public class OutboxEvent {

    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    /** Lo asigna Oracle (IDENTITY ORDER en schema.sql). En H2 de test puede quedar null. */
    @Generated(event = EventType.INSERT)
    @Column(name = "event_sequence", insertable = false, updatable = false)
    private Long eventSequence;

    @Column(name = "aggregate_id", length = 80, nullable = false)
    private String aggregateId;

    @Column(name = "event_type", length = 60, nullable = false)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            String eventId,
            String aggregateId,
            String eventType,
            String payload,
            Instant createdAt
    ) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.attempts = 0;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getEventSequence() {
        return eventSequence;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void markPublished(Instant at) {
        this.publishedAt = at;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts++;
        if (error == null) {
            this.lastError = null;
            return;
        }
        this.lastError = error.length() <= 500 ? error : error.substring(0, 500);
    }
}
