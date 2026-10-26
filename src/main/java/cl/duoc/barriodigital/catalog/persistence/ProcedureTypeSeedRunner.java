package cl.duoc.barriodigital.catalog.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la carga inicial al arrancar, después de comprobar que Oracle responde.
 */
@Component
@Order(1)
public class ProcedureTypeSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcedureTypeSeedRunner.class);

    private final ProcedureTypeSeed seed;

    /** Recibe el componente que inserta los tipos faltantes. */
    public ProcedureTypeSeedRunner(ProcedureTypeSeed seed) {
        this.seed = seed;
    }

    /** Inserta lo que falte y registra cuántas filas nuevas hubo. */
    @Override
    public void run(ApplicationArguments args) {
        int inserted = seed.insertMissing();
        log.info("Catalogo: seed de tipos de tramite, filas nuevas {}", inserted);
    }
}
