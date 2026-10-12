package cl.duoc.barriodigital.catalog.web;

import cl.duoc.barriodigital.catalog.service.CatalogService;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tipos de trámite persistidos. El alta y la edición vienen en los issues siguientes. */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/procedures")
    public List<ProcedureView> procedures() {
        return catalogService.listProcedures();
    }
}
