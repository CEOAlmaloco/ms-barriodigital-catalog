package cl.duoc.barriodigital.catalog.persistence;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Inserta los seis tipos iniciales si todavía no están.
 * Se guardan con dailyQuota null, sin límite. No reemplaza un nombre ya editado: solo corrige la tilde si alumbrado sigue diciendo "Alumbrado publico".
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

    /** Recibe el repositorio y el reloj de Chile para fechar las filas nuevas. */
    public ProcedureTypeSeed(ProcedureTypeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Inserta los códigos que faltan y devuelve cuántas filas nuevas guardó. */
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

    /** Si alumbrado conserva la etiqueta sin tilde, la reemplaza por "Alumbrado público". */
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

    /** Código y nombre de una fila de la carga inicial. */
    public record SeedRow(String value, String label) {
    }
}
