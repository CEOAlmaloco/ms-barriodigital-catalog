package cl.duoc.barriodigital.catalog.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditEventFactoryTest {

    private static final Instant NOW = Instant.parse("2026-11-12T15:00:00Z");

    @AfterEach
    void clear() {
        ActorContext.clear();
    }

    @Test
    void armaSobreProcedureTypeConActorYBeforeAfter() {
        ActorContext.set(new AuditActor(
                "2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d",
                "Admin Test",
                "Admin",
                "203.0.113.25"));
        AuditEventFactory factory = new AuditEventFactory(Clock.fixed(NOW, ZoneOffset.UTC));

        ProcedureTypeAuditState before = new ProcedureTypeAuditState("Bache en calle", true, 20, null);
        ProcedureTypeAuditState after = new ProcedureTypeAuditState("Bache en calle", true, 15, 5);
        AuditEnvelope event = factory.create("procedure-type", "bache", "updated", before, after);

        assertThat(event.eventType()).isEqualTo("procedure-type.updated");
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.producer()).isEqualTo("catalog");
        assertThat(event.key()).isEqualTo("procedure-type:bache");
        assertThat(event.actor().id()).isEqualTo("2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d");
        assertThat(event.actor().name()).isEqualTo("Admin Test");
        assertThat(event.actor().role()).isEqualTo("Admin");
        assertThat(event.actor().sourceIp()).isEqualTo("203.0.113.25");
        assertThat(event.payload().resourceType()).isEqualTo("procedure-type");
        assertThat(event.payload().resourceId()).isEqualTo("bache");
        assertThat(event.payload().action()).isEqualTo("updated");
        assertThat(event.payload().before()).isEqualTo(before);
        assertThat(event.payload().after()).isEqualTo(after);
        assertThat(event.eventId()).isNotNull();
        assertThat(event.traceId()).isNotNull();
    }

    @Test
    void createdLlevaBeforeNull() {
        ActorContext.set(new AuditActor("u1", "Admin", "Admin", null));
        AuditEventFactory factory = new AuditEventFactory(Clock.fixed(NOW, ZoneOffset.UTC));

        AuditEnvelope event = factory.create(
                "procedure-type",
                "semaforo",
                "created",
                null,
                new ProcedureTypeAuditState("Semáforo", true, null, 5));

        assertThat(event.eventType()).isEqualTo("procedure-type.created");
        assertThat(event.key()).isEqualTo("procedure-type:semaforo");
        assertThat(event.payload().before()).isNull();
        assertThat(event.actor().sourceIp()).isNull();
    }
}
