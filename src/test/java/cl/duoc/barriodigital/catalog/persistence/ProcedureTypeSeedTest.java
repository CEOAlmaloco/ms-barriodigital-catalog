package cl.duoc.barriodigital.catalog.persistence;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcedureTypeSeedTest {

    @Test
    void insertaLosSeisCodigosSiLaTablaEstaVacia() {
        ProcedureTypeRepository repository = mock(ProcedureTypeRepository.class);
        when(repository.existsById(any())).thenReturn(false);
        ProcedureTypeSeed seed = new ProcedureTypeSeed(repository);

        int inserted = seed.insertMissing();

        ArgumentCaptor<ProcedureType> saved = ArgumentCaptor.forClass(ProcedureType.class);
        verify(repository, times(6)).save(saved.capture());
        assertEquals(6, inserted);
        assertEquals(
                List.of("bache", "alumbrado", "basura", "agua", "ruido", "otro"),
                saved.getAllValues().stream().map(ProcedureType::getValue).toList()
        );
        assertEquals(ProcedureTypeSeed.INITIAL_DAILY_QUOTA, saved.getValue().getDailyQuota());
        assertEquals(saved.getValue().getDailyQuota(), saved.getValue().getRemainingQuota());
    }

    @Test
    void noPisaUnaFilaQueYaExiste() {
        ProcedureTypeRepository repository = mock(ProcedureTypeRepository.class);
        Set<String> existing = new HashSet<>(Set.of("bache", "agua"));
        when(repository.existsById(any())).thenAnswer(invocation -> existing.contains(invocation.getArgument(0)));
        ProcedureTypeSeed seed = new ProcedureTypeSeed(repository);

        int inserted = seed.insertMissing();

        ArgumentCaptor<ProcedureType> saved = ArgumentCaptor.forClass(ProcedureType.class);
        verify(repository, times(4)).save(saved.capture());
        assertEquals(4, inserted);
        List<String> values = saved.getAllValues().stream().map(ProcedureType::getValue).toList();
        assertFalse(values.contains("bache"));
        assertFalse(values.contains("agua"));
    }
}
