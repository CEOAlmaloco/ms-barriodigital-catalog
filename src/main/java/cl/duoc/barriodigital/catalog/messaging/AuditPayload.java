package cl.duoc.barriodigital.catalog.messaging;

/** Payload de un evento administrativo en audit.timeline. */
public record AuditPayload(
        String resourceType,
        String resourceId,
        String action,
        Object before,
        Object after
) {
}
