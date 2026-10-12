package cl.duoc.barriodigital.catalog.web.dto;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;

import java.time.LocalDate;

/** Lo que ve el cliente en GET /api/catalog/procedures. */
public record ProcedureView(
        String value,
        String label,
        boolean active,
        int dailyQuota,
        int remainingQuota,
        LocalDate quotaResetDate
) {
    public static ProcedureView from(ProcedureType type) {
        return new ProcedureView(
                type.getValue(),
                type.getLabel(),
                type.isActive(),
                type.getDailyQuota(),
                type.getRemainingQuota(),
                type.getQuotaResetDate()
        );
    }
}
