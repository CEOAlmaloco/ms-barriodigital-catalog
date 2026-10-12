package cl.duoc.barriodigital.catalog.service;

import cl.duoc.barriodigital.catalog.persistence.ProcedureTypeRepository;
import cl.duoc.barriodigital.catalog.web.dto.ProcedureView;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lee los tipos en cada llamada. No guarda la lista en memoria:
 * un alta hecha después aparece en el siguiente GET sin reiniciar.
 */
@Service
public class CatalogService {

    private final ProcedureTypeRepository repository;

    public CatalogService(ProcedureTypeRepository repository) {
        this.repository = repository;
    }

    public List<ProcedureView> listProcedures() {
        return repository.findAllByOrderByValueAsc().stream()
                .map(ProcedureView::from)
                .toList();
    }
}
