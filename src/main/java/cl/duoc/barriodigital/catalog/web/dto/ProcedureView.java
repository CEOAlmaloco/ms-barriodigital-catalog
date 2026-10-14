package cl.duoc.barriodigital.catalog.web.dto;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;

/** Lo que ve el cliente. quotaResetDate queda en la tabla y no se serializa. */
public record ProcedureView(
        String value,
        String label,
        boolean active,
        Integer dailyQuota,
        Integer remainingQuota
) {
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
