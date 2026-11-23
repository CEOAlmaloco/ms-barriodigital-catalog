package cl.duoc.barriodigital.catalog.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cl.duoc.barriodigital.catalog.adapter.out.persistence.OutboxEvent;
import cl.duoc.barriodigital.catalog.adapter.out.persistence.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * El JSON que catalog deja en el outbox, y que el relay publica tal cual en audit.timeline, cumple los esquemas de
 * contracts/events. Un cambio de campos obliga a actualizar el esquema y a decidir si sube schemaVersion
 * (kafka-topicos.md, sección 11).
 */
class EventSchemaContractTest {

    private static final Path EVENTS = Path.of("contracts", "events");
    private static final Instant NOW = Instant.parse("2026-11-12T12:20:31.774Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG = SchemaValidatorsConfig.builder()
            .formatAssertionsEnabled(true)
            .build();

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AuditEventFactory events = new AuditEventFactory(clock);

    @AfterEach
    void clearActor() {
        ActorContext.clear();
    }

    @Test
    void losEventosDeCreacionYCambioDeTipoCumplenSuEsquema() throws Exception {
        ActorContext.set(new AuditActor("2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d", "Admin Test", "Admin", "203.0.113.25"));
        JsonNode updated = published(events.create("procedure-type", "bache", "updated",
                new ProcedureTypeAuditState("Bache en calle", true, 20, null),
                new ProcedureTypeAuditState("Bache en calle", true, 15, 5)));
        ActorContext.set(new AuditActor("2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d", "Admin Test", "Admin", null));
        JsonNode created = published(events.create("procedure-type", "semaforo", "created", null,
                new ProcedureTypeAuditState("Semáforo", true, null, 5)));

        assertThat(schema("procedure-type.updated").validate(updated)).isEmpty();
        assertThat(schema("procedure-type.created").validate(created)).isEmpty();
    }

    @Test
    void cadaEsquemaCorrespondeAUnaAccionQueCatalogAudita() throws Exception {
        try (Stream<Path> files = Files.list(EVENTS)) {
            Set<String> schemas = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".v1.schema.json"))
                    .collect(Collectors.toSet());
            assertThat(schemas).containsExactlyInAnyOrder(
                    "procedure-type.created.v1.schema.json", "procedure-type.updated.v1.schema.json");
        }
    }

    @Test
    void unCampoNuevoEnElEstadoOLaSequenceDeRequestsRompenElEsquema() throws Exception {
        ActorContext.set(new AuditActor("u1", "Admin Test", "Admin", null));
        ObjectNode event = (ObjectNode) published(events.create("procedure-type", "bache", "updated",
                new ProcedureTypeAuditState("Bache en calle", true, 20, null),
                new ProcedureTypeAuditState("Bache en calle", true, 15, 5)));
        JsonSchema schema = schema("procedure-type.updated");

        ObjectNode extraField = event.deepCopy();
        ((ObjectNode) extraField.get("payload").get("after")).put("remainingQuota", 3);
        ObjectNode withSequence = event.deepCopy();
        withSequence.put("sequence", 1);
        ObjectNode createdWithBefore = event.deepCopy();
        createdWithBefore.put("eventType", "procedure-type.created");

        assertThat(schema.validate(extraField)).isNotEmpty();
        assertThat(schema.validate(withSequence)).isNotEmpty();
        assertThat(schema("procedure-type.created").validate(createdWithBefore)).isNotEmpty();
    }

    /** El JSON del outbox, que es el mismo que publica el relay. */
    private JsonNode published(AuditEnvelope envelope) throws Exception {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        new AuditOutboxRecorder(outbox, new ObjectMapper(), clock).record(envelope);
        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(saved.capture());
        return JSON.readTree(saved.getValue().getPayload());
    }

    private static JsonSchema schema(String eventType) {
        Path file = EVENTS.resolve(eventType + ".v1.schema.json");
        return FACTORY.getSchema(SchemaLocation.of(file.toAbsolutePath().toUri().toString()), CONFIG);
    }
}
