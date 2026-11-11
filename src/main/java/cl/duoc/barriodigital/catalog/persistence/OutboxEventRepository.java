package cl.duoc.barriodigital.catalog.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Acceso al outbox de auditoría de catalog. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /** Hasta 100 pendientes, en orden de publicación (createdAt desempata en H2 sin IDENTITY). */
    List<OutboxEvent> findFirst100ByPublishedAtIsNullOrderByEventSequenceAscCreatedAtAsc();

    long countByPublishedAtIsNull();
}
