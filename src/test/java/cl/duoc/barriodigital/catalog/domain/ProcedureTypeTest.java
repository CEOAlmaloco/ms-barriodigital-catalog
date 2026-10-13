package cl.duoc.barriodigital.catalog.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcedureTypeTest {

    @Test
    void elCupoNuevoConservaLoYaUsadoYNoCambiaElCodigo() {
        ProcedureType type = new ProcedureType("bache", "Bache en calle", true, 10, LocalDate.parse("2026-10-09"));
        type.consume(6);

        type.updateDetails("Bache grave", false, 8);

        assertEquals("bache", type.getValue());
        assertEquals("Bache grave", type.getLabel());
        assertEquals(false, type.isActive());
        assertEquals(8, type.getDailyQuota());
        assertEquals(2, type.getRemainingQuota());
    }

    @Test
    void siElCupoNuevoNoAlcanzaLoUsadoQuedaEnCero() {
        ProcedureType type = new ProcedureType("bache", "Bache en calle", 10, LocalDate.parse("2026-10-09"));
        type.consume(6);

        type.updateDetails("Bache", true, 3);

        assertEquals(3, type.getDailyQuota());
        assertEquals(0, type.getRemainingQuota());
        assertEquals("bache", type.getValue());
    }

    @Test
    void elPrimerUsoDeUnDiaNuevoReponeElCupo() {
        ProcedureType type = new ProcedureType("bache", "Bache en calle", 10, LocalDate.parse("2026-10-12"));
        type.consume(8);

        boolean changed = type.resetForToday(LocalDate.parse("2026-10-13"));

        assertTrue(changed);
        assertEquals(10, type.getRemainingQuota());
        assertEquals(10, type.getDailyQuota());
        assertEquals(LocalDate.parse("2026-10-13"), type.getQuotaResetDate());
    }

    @Test
    void elMismoDiaNoReponeLoYaUsado() {
        ProcedureType type = new ProcedureType("bache", "Bache en calle", 10, LocalDate.parse("2026-10-13"));
        type.consume(4);

        boolean changed = type.resetForToday(LocalDate.parse("2026-10-13"));

        assertFalse(changed);
        assertEquals(6, type.getRemainingQuota());
        assertEquals(LocalDate.parse("2026-10-13"), type.getQuotaResetDate());
    }
}
