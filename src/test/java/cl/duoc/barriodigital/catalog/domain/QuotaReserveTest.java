package cl.duoc.barriodigital.catalog.domain;

import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.service.CatalogService;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaReserveTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.parse("2026-10-13");

    private final ProcedureTypeRepository repository = mock(ProcedureTypeRepository.class);
    private final CatalogService service = new CatalogService(repository, mock(EntityManager.class), CLOCK);

    @Test
    void reservarEnUnDiaNuevoReponeYDespuesDescuenta() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", 10, LocalDate.parse("2026-10-12"));
        bache.consume(10);
        when(repository.findByValueForUpdate("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcedureView view = service.reserve("bache");

        assertEquals(9, view.remainingQuota());
        assertEquals(TODAY, bache.getQuotaResetDate());
        verify(repository).save(bache);
    }

    @Test
    void reservarSinCupoElMismoDiaResponde409() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", 10, TODAY);
        bache.consume(10);
        when(repository.findByValueForUpdate("bache")).thenReturn(Optional.of(bache));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.reserve("bache"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(0, bache.getRemainingQuota());
        verify(repository, never()).save(any());
    }

    @Test
    void reservarSinLimiteNoDescuentaNiResponde409() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", null, TODAY);
        when(repository.findByValueForUpdate("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcedureView view = service.reserve("bache");

        assertNull(view.dailyQuota());
        assertNull(view.remainingQuota());
    }

    @Test
    void liberarSumaUnoYNoPasaDelCupoDiario() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", 2, TODAY);
        bache.consume(1);
        when(repository.findByValueForUpdate("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(2, service.release(" bache ").remainingQuota());
        assertEquals(2, service.release("bache").remainingQuota());
    }

    @Test
    void liberarSinLimiteNoCambiaElContador() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", null, TODAY);
        when(repository.findByValueForUpdate("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcedureView view = service.release("bache");

        assertNull(view.remainingQuota());
    }

    @Test
    void unCodigoInexistenteResponde404() {
        when(repository.findByValueForUpdate("no-esta")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.reserve("no-esta"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(repository, never()).save(any());
    }
}
