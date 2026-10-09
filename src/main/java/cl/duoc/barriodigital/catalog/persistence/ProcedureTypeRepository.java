package cl.duoc.barriodigital.catalog.persistence;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcedureTypeRepository extends JpaRepository<ProcedureType, String> {
}
