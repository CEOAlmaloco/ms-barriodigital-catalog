package cl.duoc.barriodigital.catalog.persistence;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Inserta los 6 códigos que ya usa requests si todavía no están.
 * No pisa filas existentes: un reinicio no borra un label editado después.
 */
@Component
public class ProcedureTypeSeed {

    public static final int INITIAL_DAILY_QUOTA = 10;

    public static final List<SeedRow> ROWS = List.of(
            new SeedRow("bache", "Bache en calle"),
            new SeedRow("alumbrado", "Alumbrado publico"),
            new SeedRow("basura", "Basura o limpieza"),
            new SeedRow("agua", "Agua o alcantarillado"),
            new SeedRow("ruido", "Ruido molesto"),
            new SeedRow("otro", "Otro")
    );

    private final ProcedureTypeRepository repository;

    public ProcedureTypeSeed(ProcedureTypeRepository repository) {
        this.repository = repository;
    }

    public int insertMissing() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int inserted = 0;
        for (SeedRow row : ROWS) {
            if (repository.existsById(row.value())) {
                continue;
            }
            repository.save(new ProcedureType(row.value(), row.label(), INITIAL_DAILY_QUOTA, today));
            inserted++;
        }
        return inserted;
    }

    public record SeedRow(String value, String label) {
    }
}
