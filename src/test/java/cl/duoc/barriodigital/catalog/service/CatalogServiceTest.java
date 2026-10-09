package cl.duoc.barriodigital.catalog.service;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
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

    private List<String> codes() {
        return service.listProcedures().stream().map(ProcedureView::value).toList();
    }
}
