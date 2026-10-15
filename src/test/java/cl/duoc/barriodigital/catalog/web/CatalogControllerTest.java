package cl.duoc.barriodigital.catalog.web;

import cl.duoc.barriodigital.catalog.service.CatalogService;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import cl.duoc.barriodigital.catalog.web.dto.UpdateProcedureRequest;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogController.class)
class CatalogControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CatalogService catalogService;

    @Test
    void getProceduresDevuelveLosTiposPersistidos() throws Exception {
        when(catalogService.listProcedures()).thenReturn(List.of(
                new ProcedureView("bache", "Bache en calle", true, 10, 10)
        ));

        mockMvc.perform(get("/api/catalog/procedures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("bache"))
                .andExpect(jsonPath("$[0].label").value("Bache en calle"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].dailyQuota").value(10))
                .andExpect(jsonPath("$[0].remainingQuota").value(10))
                .andExpect(jsonPath("$[0].quotaResetDate").doesNotExist());
    }

    @Test
    void postDeAdminResponde201ConElTipoCreado() throws Exception {
        when(catalogService.create(any(CreateProcedureRequest.class), eq("Admin"))).thenReturn(
                new ProcedureView("semaforo", "Semáforo en mal estado", true, 10, 10)
        );

        mockMvc.perform(post("/api/catalog/procedures")
                        .header("X-User-Roles", "Admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"semaforo","label":"Semáforo en mal estado","active":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value("semaforo"))
                .andExpect(jsonPath("$.label").value("Semáforo en mal estado"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void postSinAdminResponde403() throws Exception {
        when(catalogService.create(any(CreateProcedureRequest.class), eq("Vecino")))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Admin puede crear tipos de tramite"));

        mockMvc.perform(post("/api/catalog/procedures")
                        .header("X-User-Roles", "Vecino")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"semaforo","label":"Semáforo en mal estado"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo Admin puede crear tipos de tramite"));
    }

    @Test
    void postDuplicadoResponde409() throws Exception {
        when(catalogService.create(any(CreateProcedureRequest.class), eq("Admin")))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "El codigo value ya existe"));

        mockMvc.perform(post("/api/catalog/procedures")
                        .header("X-User-Roles", "Admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"bache","label":"Bache"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El codigo value ya existe"));
    }

    @Test
    void putDeAdminResponde200SinCambiarElCodigo() throws Exception {
        when(catalogService.update(eq("bache"), any(UpdateProcedureRequest.class), eq("Admin"))).thenReturn(
                new ProcedureView("bache", "Bache grave", false, 4, 4)
        );

        mockMvc.perform(put("/api/catalog/procedures/bache")
                        .header("X-User-Roles", "Admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Bache grave","active":false,"dailyQuota":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("bache"))
                .andExpect(jsonPath("$.label").value("Bache grave"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.dailyQuota").value(4));
    }

    @Test
    void putSinAdminResponde403() throws Exception {
        when(catalogService.update(eq("bache"), any(UpdateProcedureRequest.class), eq("Vecino")))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Admin puede editar tipos de tramite"));

        mockMvc.perform(put("/api/catalog/procedures/bache")
                        .header("X-User-Roles", "Vecino")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Bache grave","active":false,"dailyQuota":4}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo Admin puede editar tipos de tramite"));
    }

    @Test
    void putDeUnCodigoInexistenteResponde404() throws Exception {
        when(catalogService.update(eq("no-esta"), any(UpdateProcedureRequest.class), eq("Admin")))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "El tipo de tramite no existe"));

        mockMvc.perform(put("/api/catalog/procedures/no-esta")
                        .header("X-User-Roles", "Admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Nada","active":true,"dailyQuota":5}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("El tipo de tramite no existe"));
    }

    @Test
    void reserveResponde200SinPedirRol() throws Exception {
        when(catalogService.reserve("bache")).thenReturn(
                new ProcedureView("bache", "Bache en calle", true, 10, 9)
        );

        mockMvc.perform(post("/api/catalog/procedures/bache/reserve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("bache"))
                .andExpect(jsonPath("$.remainingQuota").value(9))
                .andExpect(jsonPath("$.quotaResetDate").doesNotExist());
    }

    @Test
    void reserveSinCupoResponde409() throws Exception {
        when(catalogService.reserve("bache"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "No queda cupo para este tipo de tramite"));

        mockMvc.perform(post("/api/catalog/procedures/bache/reserve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No queda cupo para este tipo de tramite"));
    }

    @Test
    void erroresDePersistenciaNoExponenOracleSqlNiExcepciones() throws Exception {
        String technical = "ORA-01407 BARRIODIGITAL.PROCEDURE_TYPES.DAILY_QUOTA update procedure_types";
        for (RuntimeException error : List.of(
                new DataIntegrityViolationException(technical),
                new TransactionSystemException(technical),
                new PersistenceException(technical)
        )) {
            when(catalogService.update(eq("bache"), any(UpdateProcedureRequest.class), eq("Admin")))
                    .thenThrow(error);

            mockMvc.perform(put("/api/catalog/procedures/bache")
                            .header("X-User-Roles", "Admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"label":"Bache","active":true,"dailyQuota":null}
                                    """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().json("""
                            {"status":500,"error":"Internal Server Error","message":"No pudimos procesar la solicitud. Intenta nuevamente."}
                            """, true));
        }
    }

    @Test
    void responseStatusException500TampocoExponeDetalles() throws Exception {
        when(catalogService.listProcedures()).thenThrow(
                new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ORA-01407 update procedure_types"));

        mockMvc.perform(get("/api/catalog/procedures"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(CatalogExceptionHandler.INTERNAL_ERROR));
    }

    @Test
    void releaseResponde200YReponeElCupo() throws Exception {
        when(catalogService.release("bache")).thenReturn(
                new ProcedureView("bache", "Bache en calle", true, 10, 8)
        );

        mockMvc.perform(post("/api/catalog/procedures/bache/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingQuota").value(8));
    }
}
