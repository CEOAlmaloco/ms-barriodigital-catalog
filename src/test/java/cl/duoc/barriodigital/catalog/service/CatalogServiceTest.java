package cl.duoc.barriodigital.catalog.service;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeSeed;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private final ProcedureTypeRepository repository = mock(ProcedureTypeRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final CatalogService service = new CatalogService(repository, entityManager);

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
                new CreateProcedureRequest(" semaforo ", " Semáforo en mal estado ", null),
                "Funcionario, Admin"
        );

        assertEquals("semaforo", created.value());
        assertEquals("Semáforo en mal estado", created.label());
        assertTrue(created.active());
        assertEquals(ProcedureTypeSeed.INITIAL_DAILY_QUOTA, created.dailyQuota());
        assertEquals(ProcedureTypeSeed.INITIAL_DAILY_QUOTA, created.remainingQuota());
        assertEquals(LocalDate.now(ZoneOffset.UTC), created.quotaResetDate());
        verify(entityManager).persist(any(ProcedureType.class));
        verify(entityManager).flush();
    }

    @Test
    void activeFalsoSeGuardaApagado() {
        when(repository.existsById("verano")).thenReturn(false);

        ProcedureView created = service.create(
                new CreateProcedureRequest("verano", "Feria de verano", false),
                "Admin"
        );

        assertEquals(false, created.active());
        verify(entityManager).persist(any(ProcedureType.class));
    }

    @Test
    void valueDuplicadoResponde409SinGuardar() {
        when(repository.existsById("bache")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.create(new CreateProcedureRequest("bache", "Otro bache", true), "Admin"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void choqueDeClaveUnicaResponde409() {
        when(repository.existsById("bache")).thenReturn(false);
        doThrow(new PersistenceException(new SQLException("unique", "23505")))
                .when(entityManager).flush();

        assertEquals(HttpStatus.CONFLICT, statusOf(() ->
                service.create(new CreateProcedureRequest("bache", "Bache", true), "ROLE_Admin")));
    }

    @Test
    void choqueDeClaveOracleResponde409() {
        when(repository.existsById("bache")).thenReturn(false);
        doThrow(new PersistenceException(new SQLException("ORA-00001", "23000", 1)))
                .when(entityManager).flush();

        assertEquals(HttpStatus.CONFLICT, statusOf(() ->
                service.create(new CreateProcedureRequest("bache", "Bache", true), "Admin")));
    }

    @Test
    void otroErrorDeIntegridadNoSeDisfrazaDeDuplicado() {
        when(repository.existsById("semaforo")).thenReturn(false);
        doThrow(new PersistenceException(new SQLException("null", "23502")))
                .when(entityManager).flush();

        assertThrows(PersistenceException.class, () ->
                service.create(new CreateProcedureRequest("semaforo", "Semáforo", true), "Admin"));
    }

    @Test
    void vecinoFuncionarioYSinRolReciben403() {
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "Semáforo", true), "Vecino")));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "Semáforo", true), "Funcionario")));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "Semáforo", true), null)));
        verify(repository, never()).existsById(any());
    }

    @Test
    void valueVacioOConMayusculaEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.create(new CreateProcedureRequest("  ", "Semáforo", true), "Admin")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.create(new CreateProcedureRequest("Semáforo", "Semáforo", true), "Admin")));
        verify(entityManager, never()).persist(any());
    }

    @Test
    void labelVacioEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "   ", true), "Admin")));
    }

    private HttpStatusCode statusOf(Runnable call) {
        return assertThrows(ResponseStatusException.class, call::run).getStatusCode();
    }

    private List<String> codes() {
        return service.listProcedures().stream().map(ProcedureView::value).toList();
    }
}
