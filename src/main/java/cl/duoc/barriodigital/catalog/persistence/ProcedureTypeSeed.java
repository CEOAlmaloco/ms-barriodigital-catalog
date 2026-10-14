package cl.duoc.barriodigital.catalog.persistence;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Inserta los 6 códigos que ya usa requests si todavía no están.
 * El cupo nace en null (sin límite). No pisa un label ya editado:
 * solo corrige la tilde si la fila sigue diciendo "Alumbrado publico".
 */
@Component
public class ProcedureTypeSeed {

    public static final String OLD_ALUMBRADO_LABEL = "Alumbrado publico";

    public static final List<SeedRow> ROWS = List.of(
            new SeedRow("bache", "Bache en calle"),
            new SeedRow("alumbrado", "Alumbrado público"),
            new SeedRow("basura", "Basura o limpieza"),
            new SeedRow("agua", "Agua o alcantarillado"),
            new SeedRow("ruido", "Ruido molesto"),
            new SeedRow("otro", "Otro")
    );

    private final ProcedureTypeRepository repository;
    private final Clock clock;

    public ProcedureTypeSeed(ProcedureTypeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public int insertMissing() {
        LocalDate today = LocalDate.now(clock);
        int inserted = 0;
        for (SeedRow row : ROWS) {
            if (repository.existsById(row.value())) {
                correctAlumbradoAccent(row);
                continue;
            }
            repository.save(new ProcedureType(row.value(), row.label(), null, today));
            inserted++;
        }
        return inserted;
    }

    private void correctAlumbradoAccent(SeedRow row) {
        if (!"alumbrado".equals(row.value())) {
            return;
        }
        repository.findById(row.value()).ifPresent(current -> {
            if (OLD_ALUMBRADO_LABEL.equals(current.getLabel())) {
                current.replaceLabel(row.label());
                repository.save(current);
            }
        });
    }

    public record SeedRow(String value, String label) {
    }
}
