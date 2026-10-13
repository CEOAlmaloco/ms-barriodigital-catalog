package cl.duoc.barriodigital.catalog.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
