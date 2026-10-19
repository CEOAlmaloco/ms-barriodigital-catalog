package cl.duoc.barriodigital.catalog.persistence;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        ProcedureTypeSeed seed = new ProcedureTypeSeed(repository, Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneOffset.UTC));

        int inserted = seed.insertMissing();

        ArgumentCaptor<ProcedureType> saved = ArgumentCaptor.forClass(ProcedureType.class);
        verify(repository, times(6)).save(saved.capture());
        assertEquals(6, inserted);
        assertEquals(
                List.of("bache", "alumbrado", "basura", "agua", "ruido", "otro"),
                saved.getAllValues().stream().map(ProcedureType::getValue).toList()
        );
        assertNull(saved.getValue().getDailyQuota());
        assertNull(saved.getValue().getRemainingQuota());
        assertEquals(
                "Alumbrado público",
                saved.getAllValues().stream()
                        .filter(type -> "alumbrado".equals(type.getValue()))
                        .findFirst()
                        .orElseThrow()
                        .getLabel()
        );
    }

    @Test
    void corrigeLaTildeSiLaFilaSigueConElTextoViejo() {
        ProcedureTypeRepository repository = mock(ProcedureTypeRepository.class);
        ProcedureType alumbrado = new ProcedureType("alumbrado", ProcedureTypeSeed.OLD_ALUMBRADO_LABEL, 10, java.time.LocalDate.parse("2026-10-13"));
        when(repository.existsById(any())).thenReturn(true);
        when(repository.findById("alumbrado")).thenReturn(Optional.of(alumbrado));
        ProcedureTypeSeed seed = new ProcedureTypeSeed(repository, Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneOffset.UTC));

        int inserted = seed.insertMissing();

        assertEquals(0, inserted);
        assertEquals("Alumbrado público", alumbrado.getLabel());
        verify(repository).save(alumbrado);
    }

    @Test
    void noPisaUnaFilaQueYaExiste() {
        ProcedureTypeRepository repository = mock(ProcedureTypeRepository.class);
        Set<String> existing = new HashSet<>(Set.of("bache", "agua"));
        when(repository.existsById(any())).thenAnswer(invocation -> existing.contains(invocation.getArgument(0)));
        ProcedureTypeSeed seed = new ProcedureTypeSeed(repository, Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneOffset.UTC));

        int inserted = seed.insertMissing();

        ArgumentCaptor<ProcedureType> saved = ArgumentCaptor.forClass(ProcedureType.class);
        verify(repository, times(4)).save(saved.capture());
        assertEquals(4, inserted);
        List<String> values = saved.getAllValues().stream().map(ProcedureType::getValue).toList();
        assertFalse(values.contains("bache"));
        assertFalse(values.contains("agua"));
    }
}
