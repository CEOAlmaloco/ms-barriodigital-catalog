package cl.duoc.barriodigital.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Arranca el servicio de catálogo.
 */
@SpringBootApplication
public class CatalogApplication {

    /** Inicia Spring Boot con esta clase como configuración. */
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
