package cl.duoc.barriodigital.catalog.persistence;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accede a los tipos de trámite en Oracle.
 * Puede bloquear una fila al reservar para que dos admisiones no descuenten el mismo cupo.
 */
public interface ProcedureTypeRepository extends JpaRepository<ProcedureType, String> {

    /** Lista todos los tipos ordenados por código. */
    List<ProcedureType> findAllByOrderByValueAsc();

    /**
     * Busca un tipo y lo bloquea hasta que termine la transacción.
     * reserve y release lo usan para que dos operaciones no modifiquen el cupo a la vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProcedureType p where p.value = :value")
    Optional<ProcedureType> findByValueForUpdate(@Param("value") String value);
}
