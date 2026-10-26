package cl.duoc.barriodigital.catalog.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Al arrancar abre una conexión y registra el nombre y la versión de Oracle.
 * No escribe la clave ni la dirección completa, para no dejar secretos en el log.
 */
@Component
@Order(0)
public class OracleDataSourceProbe implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OracleDataSourceProbe.class);

    private final DataSource dataSource;

    /** Recibe el pool de conexiones que Spring ya configuró. */
    public OracleDataSourceProbe(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Comprueba que el pool responde antes de seguir con el arranque. */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            String version = connection.getMetaData().getDatabaseProductVersion();
            log.info("Oracle OK: pool conectado ({} {})", product, version);
        }
    }
}
