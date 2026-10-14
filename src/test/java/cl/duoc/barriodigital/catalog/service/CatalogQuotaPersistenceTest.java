package cl.duoc.barriodigital.catalog.service;

import cl.duoc.barriodigital.catalog.config.ClockConfig;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import cl.duoc.barriodigital.catalog.web.dto.UpdateProcedureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * H2 de verdad: reservar y liberar persisten el contador, y dos reservas a la vez no descuentan el mismo cupo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({CatalogService.class, ClockConfig.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
class CatalogQuotaPersistenceTest {

    @Autowired
    CatalogService service;

    @Autowired
    ProcedureTypeRepository repository;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
    }

    @Test
    void reservarDescuentaLiberarReponeYElCeroResponde409() {
        service.create(new CreateProcedureRequest("semaforo", "Semáforo", true), "Admin");
        service.update("semaforo", new UpdateProcedureRequest("Semáforo", true, 1, null), "Admin");

        ProcedureView reserved = service.reserve("semaforo");
        assertEquals(0, reserved.remainingQuota());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.reserve("semaforo"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(0, service.listProcedures().get(0).remainingQuota());

        assertEquals(1, service.release("semaforo").remainingQuota());
        assertEquals(1, service.release("semaforo").remainingQuota());
    }

    @Test
    void sinLimiteReservarYLiberarDejanElCupoEnNull() {
        service.create(new CreateProcedureRequest("bache", "Bache en calle", true), "Admin");

        ProcedureView reserved = service.reserve("bache");
        ProcedureView released = service.release("bache");

        assertNull(reserved.dailyQuota());
        assertNull(reserved.remainingQuota());
        assertNull(released.remainingQuota());
    }

    @Test
    void dosReservasALaVezDejanUnaEn409() throws Exception {
        service.create(new CreateProcedureRequest("bache", "Bache en calle", true), "Admin");
        service.update("bache", new UpdateProcedureRequest("Bache en calle", true, 1, null), "Admin");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        service.reserve("bache");
                        return 200;
                    } catch (ResponseStatusException ex) {
                        return ex.getStatusCode().value();
                    }
                }));
            }
            ready.await();
            go.countDown();

            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> result : results) {
                codes.add(result.get(5, TimeUnit.SECONDS));
            }
            codes.sort(Integer::compareTo);
            assertEquals(List.of(200, 409), codes);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, service.listProcedures().get(0).remainingQuota());
    }
}
