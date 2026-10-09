package cl.duoc.barriodigital.catalog.web;

import cl.duoc.barriodigital.catalog.service.CatalogService;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tipos de trámite persistidos. La edición es EP1.5-12. */
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

    @PostMapping("/procedures")
    public ResponseEntity<ProcedureView> create(
            @RequestBody CreateProcedureRequest body,
            @RequestHeader(value = AdminCaller.HEADER_USER_ROLES, required = false) String roles
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.create(body, roles));
    }
}
