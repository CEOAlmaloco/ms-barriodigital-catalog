package cl.duoc.barriodigital.catalog.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class ProcedureTypeSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcedureTypeSeedRunner.class);

    private final ProcedureTypeSeed seed;

    public ProcedureTypeSeedRunner(ProcedureTypeSeed seed) {
        this.seed = seed;
    }

    @Override
    public void run(ApplicationArguments args) {
        int inserted = seed.insertMissing();
        log.info("Catalogo: seed de tipos de tramite, filas nuevas {}", inserted);
    }
}
