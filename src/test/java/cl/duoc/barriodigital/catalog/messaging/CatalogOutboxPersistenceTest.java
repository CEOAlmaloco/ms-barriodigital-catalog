package cl.duoc.barriodigital.catalog.messaging;

import cl.duoc.barriodigital.catalog.config.ClockConfig;
import cl.duoc.barriodigital.catalog.persistence.OutboxEvent;
import cl.duoc.barriodigital.catalog.persistence.OutboxEventRepository;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.service.CatalogService;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Rollback no deja outbox; el relay publica después del commit.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        CatalogService.class,
        ClockConfig.class,
        AuditEventFactory.class,
        AuditOutboxRecorder.class,
        CatalogOutboxPersistenceTest.OutboxTestConfig.class
})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
class CatalogOutboxPersistenceTest {

    @TestConfiguration
    static class OutboxTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
            return new TransactionTemplate(tm);
        }
    }

    @Autowired
    CatalogService service;

    @Autowired
    ProcedureTypeRepository procedureTypes;

    @Autowired
    OutboxEventRepository outbox;

    @Autowired
    TransactionTemplate tx;

    @MockBean
    AuditKafkaSender sender;

    @BeforeEach
    void limpiar() {
        ActorContext.set(new AuditActor("u1", "Admin Test", "Admin", "203.0.113.25"));
        outbox.deleteAll();
        procedureTypes.deleteAll();
    }

    @Test
    void rollbackNoDejaTipoNiEventoEnOutbox() {
        tx.executeWithoutResult(status -> {
            service.create(new CreateProcedureRequest("semaforo", "Semáforo", true, 5), "Admin");
            assertEquals(1, outbox.count());
            status.setRollbackOnly();
        });

        assertTrue(procedureTypes.findById("semaforo").isEmpty());
        assertEquals(0, outbox.count());
    }

    @Test
    void publicacionDiferidaElRelayEnviaYMarcaPublishedAt() throws Exception {
        service.create(new CreateProcedureRequest("bache", "Bache", true, null), "Admin");

        List<OutboxEvent> pending = outbox.findFirst100ByPublishedAtIsNullOrderByEventSequenceAscCreatedAtAsc();
        assertEquals(1, pending.size());
        assertNull(pending.get(0).getPublishedAt());
        assertEquals("procedure-type.created", pending.get(0).getEventType());
        assertEquals("procedure-type:bache", pending.get(0).getAggregateId());

        OutboxRelay relay = new OutboxRelay(outbox, sender, java.time.Clock.systemUTC(), tx);
        relay.publishPending();

        verify(sender).sendConfirmed(
                eq(OutboxRelay.TOPIC),
                eq("procedure-type:bache"),
                anyString()
        );
        OutboxEvent published = outbox.findById(pending.get(0).getEventId()).orElseThrow();
        assertTrue(published.getPublishedAt() != null);
        assertEquals(0, outbox.countByPublishedAtIsNull());
    }

    @Test
    void fallaDeEnvioDetieneElLoteYNoMarcaPublished() throws Exception {
        service.create(new CreateProcedureRequest("agua", "Agua", true, null), "Admin");
        service.create(new CreateProcedureRequest("luz", "Luz", true, null), "Admin");
        assertEquals(2, outbox.countByPublishedAtIsNull());

        doThrow(new RuntimeException("Topic audit.timeline not present in metadata after 5000 ms."))
                .when(sender).sendConfirmed(anyString(), anyString(), anyString());

        OutboxRelay relay = new OutboxRelay(outbox, sender, java.time.Clock.systemUTC(), tx);
        relay.publishPending();

        verify(sender, times(1)).sendConfirmed(anyString(), anyString(), anyString());
        assertEquals(2, outbox.countByPublishedAtIsNull());
        OutboxEvent first = outbox.findFirst100ByPublishedAtIsNullOrderByEventSequenceAscCreatedAtAsc().get(0);
        assertEquals(1, first.getAttempts());
        assertTrue(first.getLastError().contains("audit.timeline"));
    }
}
