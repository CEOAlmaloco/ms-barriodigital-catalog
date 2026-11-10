package cl.duoc.barriodigital.catalog.service;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import cl.duoc.barriodigital.catalog.messaging.ActorContext;
import cl.duoc.barriodigital.catalog.messaging.AuditActor;
import cl.duoc.barriodigital.catalog.messaging.AuditEnvelope;
import cl.duoc.barriodigital.catalog.messaging.AuditEventFactory;
import cl.duoc.barriodigital.catalog.messaging.AuditPublisher;
import cl.duoc.barriodigital.catalog.messaging.ProcedureTypeAuditState;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import cl.duoc.barriodigital.catalog.web.dto.UpdateProcedureRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.parse("2026-10-13");

    private final ProcedureTypeRepository repository = mock(ProcedureTypeRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final AuditEventFactory auditEvents = new AuditEventFactory(CLOCK);
    private final AuditPublisher auditPublisher = mock(AuditPublisher.class);
    private final CatalogService service = new CatalogService(
            repository, entityManager, CLOCK, auditEvents, auditPublisher);

    @BeforeEach
    void setActor() {
        ActorContext.set(new AuditActor("u1", "Admin Test", "Admin", "203.0.113.25"));
    }

    @AfterEach
    void clearTxAndActor() {
        ActorContext.clear();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void listaLoQueHayEnLaTablaOrdenadoPorCodigo() {
        LocalDate today = LocalDate.parse("2026-10-08");
        when(repository.findAllByOrderByValueAsc()).thenReturn(List.of(
                new ProcedureType("agua", "Agua o alcantarillado", 10, today),
                new ProcedureType("bache", "Bache en calle", 10, today)
        ));

        List<ProcedureView> procedures = service.listProcedures();

        assertEquals(List.of("agua", "bache"), procedures.stream().map(ProcedureView::value).toList());
        assertEquals("Bache en calle", procedures.get(1).label());
        assertEquals(10, procedures.get(1).dailyQuota());
        assertEquals(10, procedures.get(1).remainingQuota());
    }

    @Test
    void unAltaPosteriorApareceEnElSiguienteGetSinReiniciar() {
        LocalDate today = LocalDate.parse("2026-10-08");
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", 10, today);
        ProcedureType luminaria = new ProcedureType("luminaria", "Luminaria", 4, today);
        when(repository.findAllByOrderByValueAsc())
                .thenReturn(List.of(bache))
                .thenReturn(List.of(bache, luminaria));

        assertEquals(List.of("bache"), codes());

        assertEquals(List.of("bache", "luminaria"), codes());
        verify(repository, times(2)).findAllByOrderByValueAsc();
    }

    @Test
    void adminCreaElTipoConEtiquetaRecortadaYActivoPorDefecto() {
        when(repository.existsById("semaforo")).thenReturn(false);

        ProcedureView created = service.create(
                new CreateProcedureRequest(" semaforo ", " Semáforo en mal estado ", null, null),
                "Funcionario, Admin"
        );

        assertEquals("semaforo", created.value());
        assertEquals("Semáforo en mal estado", created.label());
        assertTrue(created.active());
        assertNull(created.dailyQuota());
        assertNull(created.remainingQuota());
        assertNull(created.slaBusinessDays());
        verify(entityManager).persist(any(ProcedureType.class));
        verify(entityManager).flush();
    }

    @Test
    void adminCreaElTipoConPlazoOpcional() {
        when(repository.existsById("semaforo")).thenReturn(false);

        ProcedureView created = service.create(
                new CreateProcedureRequest("semaforo", "Semáforo", true, 5),
                "Admin"
        );

        assertEquals(5, created.slaBusinessDays());
        verify(entityManager).persist(any(ProcedureType.class));
    }

    @Test
    void altaConPlazoFueraDeRangoEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "Semáforo", true, 0), "Admin")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "Semáforo", true, 121), "Admin")));
        verify(entityManager, never()).persist(any());
    }

    @Test
    void activeFalsoSeGuardaApagado() {
        when(repository.existsById("verano")).thenReturn(false);

        ProcedureView created = service.create(
                new CreateProcedureRequest("verano", "Feria de verano", false, null),
                "Admin"
        );

        assertEquals(false, created.active());
        verify(entityManager).persist(any(ProcedureType.class));
    }

    @Test
    void valueDuplicadoResponde409SinGuardar() {
        when(repository.existsById("bache")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.create(new CreateProcedureRequest("bache", "Otro bache", true, null), "Admin"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void choqueDeClaveUnicaResponde409() {
        when(repository.existsById("bache")).thenReturn(false);
        doThrow(new PersistenceException(new SQLException("unique", "23505")))
                .when(entityManager).flush();

        assertEquals(HttpStatus.CONFLICT, statusOf(() ->
                service.create(new CreateProcedureRequest("bache", "Bache", true, null), "ROLE_Admin")));
    }

    @Test
    void choqueDeClaveOracleResponde409() {
        when(repository.existsById("bache")).thenReturn(false);
        doThrow(new PersistenceException(new SQLException("ORA-00001", "23000", 1)))
                .when(entityManager).flush();

        assertEquals(HttpStatus.CONFLICT, statusOf(() ->
                service.create(new CreateProcedureRequest("bache", "Bache", true, null), "Admin")));
    }

    @Test
    void otroErrorDeIntegridadNoSeDisfrazaDeDuplicado() {
        when(repository.existsById("semaforo")).thenReturn(false);
        doThrow(new PersistenceException(new SQLException("null", "23502")))
                .when(entityManager).flush();

        assertThrows(PersistenceException.class, () ->
                service.create(new CreateProcedureRequest("semaforo", "Semáforo", true, null), "Admin"));
    }

    @Test
    void vecinoFuncionarioYSinRolReciben403() {
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "Semáforo", true, null), "Vecino")));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "Semáforo", true, null), "Funcionario")));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "Semáforo", true, null), null)));
        verify(repository, never()).existsById(any());
    }

    @Test
    void valueVacioOConMayusculaEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.create(new CreateProcedureRequest("  ", "Semáforo", true, null), "Admin")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.create(new CreateProcedureRequest("Semáforo", "Semáforo", true, null), "Admin")));
        verify(entityManager, never()).persist(any());
    }

    @Test
    void labelVacioEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "   ", true, null), "Admin")));
    }

    @Test
    void adminEditaEtiquetaActivoYCupoSinCambiarElCodigo() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", true, 10, TODAY);
        when(repository.findById("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcedureView updated = service.update(
                " bache ",
                new UpdateProcedureRequest(" Bache grave ", false, 4, null, "bache"),
                "Admin"
        );

        assertEquals("bache", updated.value());
        assertEquals("Bache grave", updated.label());
        assertFalse(updated.active());
        assertEquals(4, updated.dailyQuota());
        assertEquals(4, updated.remainingQuota());
        assertEquals(TODAY, bache.getQuotaResetDate());
    }

    @Test
    void unPutSinCupoDejaElTipoSinLimite() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", true, 10, TODAY);
        when(repository.findById("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcedureView updated = service.update(
                "bache",
                new UpdateProcedureRequest("Bache en calle", true, null, null, null),
                "Admin"
        );

        assertNull(updated.dailyQuota());
        assertNull(updated.remainingQuota());
        assertNull(updated.slaBusinessDays());
    }

    @Test
    void adminEditaElPlazoYUnPutSinPlazoLoBorra() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", true, 10, null, TODAY);
        when(repository.findById("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcedureView withSla = service.update(
                "bache",
                new UpdateProcedureRequest("Bache en calle", true, 10, 5, null),
                "Admin"
        );
        assertEquals(5, withSla.slaBusinessDays());

        ProcedureView withoutSla = service.update(
                "bache",
                new UpdateProcedureRequest("Bache en calle", true, 10, null, null),
                "Admin"
        );
        assertNull(withoutSla.slaBusinessDays());
    }

    @Test
    void edicionConPlazoFueraDeRangoEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("Bache", true, 5, 0, null), "Admin")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("Bache", true, 5, 121, null), "Admin")));
        verify(repository, never()).findById(any());
    }

    @Test
    void tipoInexistenteResponde404() {
        when(repository.findById("no-esta")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, statusOf(() ->
                service.update("no-esta", new UpdateProcedureRequest("Nada", true, 5, null, null), "Admin")));
        verify(repository, never()).save(any());
    }

    @Test
    void noSePuedeCambiarElValue() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("Bache", true, 5, null, "otro"), "Admin")));
        verify(repository, never()).findById(any());
    }

    @Test
    void edicionSinAdminResponde403() {
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("Bache", true, 5, null, null), "Vecino")));
        verify(repository, never()).findById(any());
    }

    @Test
    void edicionSinEtiquetaActivoOCupoEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("  ", true, 5, null, null), "Admin")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("Bache", null, 5, null, null), "Admin")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("Bache", true, 0, null, null), "Admin")));
        verify(repository, never()).findById(any());
    }

    @Test
    void altaPublicaProcedureTypeCreatedDespuesDelCommit() {
        when(repository.existsById("semaforo")).thenReturn(false);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.create(new CreateProcedureRequest("semaforo", "Semáforo", true, 5), "Admin");

        verify(auditPublisher, never()).publish(any());
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

        ArgumentCaptor<AuditEnvelope> captor = ArgumentCaptor.forClass(AuditEnvelope.class);
        verify(auditPublisher).publish(captor.capture());
        AuditEnvelope event = captor.getValue();
        assertEquals("procedure-type.created", event.eventType());
        assertEquals("procedure-type:semaforo", event.key());
        assertNull(event.payload().before());
        assertEquals(new ProcedureTypeAuditState("Semáforo", true, null, 5), event.payload().after());
        assertEquals("203.0.113.25", event.actor().sourceIp());
    }

    @Test
    void edicionPublicaProcedureTypeUpdatedConBeforeYAfter() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", true, 20, null, TODAY);
        when(repository.findById("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.update(
                "bache",
                new UpdateProcedureRequest("Bache en calle", true, 15, 5, null),
                "Admin"
        );

        verify(auditPublisher, never()).publish(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

        ArgumentCaptor<AuditEnvelope> captor = ArgumentCaptor.forClass(AuditEnvelope.class);
        verify(auditPublisher).publish(captor.capture());
        AuditEnvelope event = captor.getValue();
        assertEquals("procedure-type.updated", event.eventType());
        assertEquals(new ProcedureTypeAuditState("Bache en calle", true, 20, null), event.payload().before());
        assertEquals(new ProcedureTypeAuditState("Bache en calle", true, 15, 5), event.payload().after());
    }

    @Test
    void altaFallidaNoPublicaAuditoria() {
        when(repository.existsById("bache")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () ->
                service.create(new CreateProcedureRequest("bache", "Otro", true, null), "Admin"));

        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void reserveNoPublicaAuditoria() {
        ProcedureType bache = new ProcedureType("bache", "Bache", true, 2, TODAY);
        when(repository.findByValueForUpdate("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reserve("bache");

        verify(auditPublisher, never()).publish(any());
    }

    private HttpStatusCode statusOf(Runnable call) {
        return assertThrows(ResponseStatusException.class, call::run).getStatusCode();
    }

    private List<String> codes() {
        return service.listProcedures().stream().map(ProcedureView::value).toList();
    }
}
