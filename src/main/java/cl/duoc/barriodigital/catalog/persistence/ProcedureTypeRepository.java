package cl.duoc.barriodigital.catalog.persistence;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcedureTypeRepository extends JpaRepository<ProcedureType, String> {

    List<ProcedureType> findAllByOrderByValueAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProcedureType p where p.value = :value")
    Optional<ProcedureType> findByValueForUpdate(@Param("value") String value);
}
