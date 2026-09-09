package cl.duoc.barriodigital.catalog.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Esqueleto EP1-25. El CRUD de tipos/cupos viene en EP2.
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    @GetMapping("/procedures")
    public List<Map<String, Object>> procedures() {
        return List.of(
                Map.of(
                        "id", "proc-basura",
                        "name", "Retiro de escombros",
                        "dailyQuota", 10
                ),
                Map.of(
                        "id", "proc-ruido",
                        "name", "Denuncia por ruido",
                        "dailyQuota", 20
                )
        );
    }
}
