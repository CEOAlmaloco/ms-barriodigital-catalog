package cl.duoc.barriodigital.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Publica un reloj fijo en America/Santiago.
 * El cupo diario cambia de día con esa zona, no con la hora del servidor.
 */
@Configuration
public class ClockConfig {

    /** Entrega el reloj que usa el reset del cupo. */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("America/Santiago"));
    }
}
