package cl.duoc.barriodigital.catalog.web.dto;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;

/**
 * Respuesta de un tipo: código, nombre, si está activo y el cupo.
 * No incluye quotaResetDate: es un dato interno del reset en hora de Chile.
 */
public record ProcedureView(
        String value,
        String label,
        boolean active,
        Integer dailyQuota,
        Integer remainingQuota
) {
    /** Arma la respuesta a partir de la entidad persistida. */
    public static ProcedureView from(ProcedureType type) {
        return new ProcedureView(
                type.getValue(),
                type.getLabel(),
                type.isActive(),
                type.getDailyQuota(),
                type.getRemainingQuota()
        );
    }
}
