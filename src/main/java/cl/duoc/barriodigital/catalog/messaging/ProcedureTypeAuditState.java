package cl.duoc.barriodigital.catalog.messaging;

import cl.duoc.barriodigital.catalog.domain.ProcedureType;

/**
 * before/after de procedure-type en audit.timeline.
 * No incluye remainingQuota ni quotaResetDate.
 */
public record ProcedureTypeAuditState(
        String label,
        boolean active,
        Integer dailyQuota,
        Integer slaBusinessDays
) {
    public static ProcedureTypeAuditState from(ProcedureType type) {
        return new ProcedureTypeAuditState(
                type.getLabel(),
                type.isActive(),
                type.getDailyQuota(),
                type.getSlaBusinessDays()
        );
    }
}
