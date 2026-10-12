package cl.duoc.barriodigital.catalog.web;

import cl.duoc.barriodigital.catalog.service.CatalogService;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
                new ProcedureView("bache", "Bache en calle", true, 10, 10, LocalDate.parse("2026-10-08"))
        ));

        mockMvc.perform(get("/api/catalog/procedures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("bache"))
                .andExpect(jsonPath("$[0].label").value("Bache en calle"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].dailyQuota").value(10))
                .andExpect(jsonPath("$[0].remainingQuota").value(10))
                .andExpect(jsonPath("$[0].quotaResetDate").value("2026-10-08"));
    }
}
