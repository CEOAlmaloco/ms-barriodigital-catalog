package cl.duoc.barriodigital.catalog.domain;

import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.service.CatalogService;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import cl.duoc.barriodigital.catalog.web.dto.UpdateProcedureRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LazyQuotaResetTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.parse("2026-10-13");

    private final ProcedureTypeRepository repository = mock(ProcedureTypeRepository.class);
    private final CatalogService service = new CatalogService(repository, mock(EntityManager.class), CLOCK);

    @Test
    void elPrimerGetDeUnDiaNuevoReponeElCupoYLoGuarda() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", 10, LocalDate.parse("2026-10-12"));
        bache.consume(8);
        when(repository.findAllByOrderByValueAsc()).thenReturn(List.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcedureView view = service.listProcedures().get(0);

        assertEquals(10, view.remainingQuota());
        assertEquals(TODAY, bache.getQuotaResetDate());
        verify(repository).save(bache);
    }

    @Test
    void elMismoDiaNoVuelveALlenarElCupo() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", 10, TODAY);
        bache.consume(4);
        when(repository.findAllByOrderByValueAsc()).thenReturn(List.of(bache));

        assertEquals(6, service.listProcedures().get(0).remainingQuota());
        verify(repository, never()).save(any());
    }

    @Test
    void unSegundoGetElMismoDiaNoReponeLoConsumidoDespuesDelReset() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", 10, LocalDate.parse("2026-10-12"));
        bache.consume(8);
        when(repository.findAllByOrderByValueAsc()).thenReturn(List.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.listProcedures();
        bache.consume(3);
        clearInvocations(repository);

        assertEquals(7, service.listProcedures().get(0).remainingQuota());
        verify(repository, never()).save(any());
    }

    @Test
    void elPutDeUnDiaNuevoNoArrastraElUsoDeAyer() {
        ProcedureType bache = new ProcedureType("bache", "Bache en calle", 10, LocalDate.parse("2026-10-12"));
        bache.consume(8);
        when(repository.findById("bache")).thenReturn(Optional.of(bache));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcedureView updated = service.update(
                "bache",
                new UpdateProcedureRequest("Bache en calle", true, 10, null),
                "Admin"
        );

        assertEquals(10, updated.remainingQuota());
        assertEquals(TODAY, bache.getQuotaResetDate());
    }
}
