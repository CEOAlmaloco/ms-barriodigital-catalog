package cl.duoc.barriodigital.catalog.service;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeSeed;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 de verdad: el segundo insert del mismo código responde 409 y deja la fila original.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(CatalogService.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
class CatalogCreatePersistenceTest {

    @Autowired
    CatalogService service;

    @Autowired
    ProcedureTypeRepository repository;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
    }

    @Test
    void unaColisionNoSobrescribeLaFilaYElGetMuestraLaOriginal() {
        service.create(new CreateProcedureRequest("semaforo", "Semáforo original", true), "Admin");

        ProcedureType repetido = new ProcedureType(
                "semaforo",
                "Nombre nuevo",
                false,
                ProcedureTypeSeed.INITIAL_DAILY_QUOTA,
                LocalDate.now(ZoneOffset.UTC)
        );
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.insertOnly(repetido));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());

        List<ProcedureView> listed = service.listProcedures();
        assertEquals(List.of("semaforo"), listed.stream().map(ProcedureView::value).toList());
        assertEquals("Semáforo original", listed.get(0).label());
        assertTrue(listed.get(0).active());
    }
}
