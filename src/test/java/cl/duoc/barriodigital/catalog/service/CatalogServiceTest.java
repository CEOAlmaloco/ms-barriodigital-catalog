package cl.duoc.barriodigital.catalog.service;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeSeed;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import cl.duoc.barriodigital.catalog.web.dto.UpdateProcedureRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogServiceTest {

    private final ProcedureTypeRepository repository = mock(ProcedureTypeRepository.class);
    private final CatalogService service = new CatalogService(repository);

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
    void adminCreaElTipoYElSiguienteGetLoVePorqueQuedoEnLaTabla() {
        when(repository.existsById("semaforo")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

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
    }

    @Test
    void activeFalsoSeGuardaApagado() {
        when(repository.existsById("verano")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcedureView created = service.create(
                new CreateProcedureRequest("verano", "Feria de verano", false),
                "Admin"
        );

        assertEquals(false, created.active());
    }

    @Test
    void valueDuplicadoResponde409SinGuardar() {
        when(repository.existsById("bache")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.create(new CreateProcedureRequest("bache", "Otro bache", true), "Admin"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void choqueDeClaveEnLaBaseTambienEs409() {
        when(repository.existsById("bache")).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("pk"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.create(new CreateProcedureRequest("bache", "Bache", true), "ROLE_Admin"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
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
        verify(repository, never()).save(any());
    }

    @Test
    void labelVacioEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.create(new CreateProcedureRequest("semaforo", "   ", true), "Admin")));
    }

    @Test
    void adminEditaEtiquetaActivoYCupoSinCambiarElCodigo() {
        LocalDate today = LocalDate.parse("2026-10-09");
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", true, 10, today);
        when(repository.findById("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcedureView updated = service.update(
                " bache ",
                new UpdateProcedureRequest(" Bache grave ", false, 4, "bache"),
                "Admin"
        );

        assertEquals("bache", updated.value());
        assertEquals("Bache grave", updated.label());
        assertFalse(updated.active());
        assertEquals(4, updated.dailyQuota());
        assertEquals(4, updated.remainingQuota());
        assertEquals(today, updated.quotaResetDate());
    }

    @Test
    void tipoInexistenteResponde404() {
        when(repository.findById("no-esta")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, statusOf(() ->
                service.update("no-esta", new UpdateProcedureRequest("Nada", true, 5, null), "Admin")));
        verify(repository, never()).save(any());
    }

    @Test
    void noSePuedeCambiarElValue() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("Bache", true, 5, "otro"), "Admin")));
        verify(repository, never()).findById(any());
    }

    @Test
    void edicionSinAdminResponde403() {
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("Bache", true, 5, null), "Vecino")));
        verify(repository, never()).findById(any());
    }

    @Test
    void edicionSinEtiquetaActivoOCupoEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("  ", true, 5, null), "Admin")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("Bache", null, 5, null), "Admin")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() ->
                service.update("bache", new UpdateProcedureRequest("Bache", true, 0, null), "Admin")));
        verify(repository, never()).findById(any());
    }

    private HttpStatusCode statusOf(Runnable call) {
        return assertThrows(ResponseStatusException.class, call::run).getStatusCode();
    }

    private List<String> codes() {
        return service.listProcedures().stream().map(ProcedureView::value).toList();
    }
}
