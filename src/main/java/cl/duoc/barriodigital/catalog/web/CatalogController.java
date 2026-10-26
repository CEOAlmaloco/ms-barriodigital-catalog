package cl.duoc.barriodigital.catalog.web;

import cl.duoc.barriodigital.catalog.service.CatalogService;
import cl.duoc.barriodigital.catalog.web.dto.CreateProcedureRequest;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import cl.duoc.barriodigital.catalog.web.dto.UpdateProcedureRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Expone listar, crear y editar tipos, y reservar o devolver un cupo.
 * reserve y release no exigen rol aquí porque solo los llama el BFF.
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    /** Recibe el servicio que aplica las reglas del catálogo. */
    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** Lista los tipos. El servicio repone el cupo si cambió el día en Chile. */
    @GetMapping("/procedures")
    public List<ProcedureView> procedures() {
        return catalogService.listProcedures();
    }

    /**
     * Crea un tipo. El rol llega en X-User-Roles; el catálogo no recibe el JWT.
     * El cupo queda sin límite.
     */
    @PostMapping("/procedures")
    public ResponseEntity<ProcedureView> create(
            @RequestBody CreateProcedureRequest body,
            @RequestHeader(value = AdminCaller.HEADER_USER_ROLES, required = false) String roles
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.create(body, roles));
    }

    /** Edita un tipo. El código va en la URL y no se cambia. */
    @PutMapping("/procedures/{value}")
    public ProcedureView update(
            @PathVariable String value,
            @RequestBody UpdateProcedureRequest body,
            @RequestHeader(value = AdminCaller.HEADER_USER_ROLES, required = false) String roles
    ) {
        return catalogService.update(value, body, roles);
    }

    /**
     * Reserva un cupo al admitir. No valida rol porque solo lo llama el BFF.
     * El descuento ocurre en el servicio.
     */
    @PostMapping("/procedures/{value}/reserve")
    public ProcedureView reserve(@PathVariable String value) {
        return catalogService.reserve(value);
    }

    /**
     * Devuelve un cupo si la admisión no se guardó. No valida rol porque solo lo llama el BFF.
     */
    @PostMapping("/procedures/{value}/release")
    public ProcedureView release(@PathVariable String value) {
        return catalogService.release(value);
    }
}
