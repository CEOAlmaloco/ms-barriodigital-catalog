package cl.duoc.barriodigital.catalog.messaging;

/**
 * Actor de la petición actual (headers X-User-* / X-Source-Ip).
 * Lo llena el filtro HTTP y lo lee el factory de auditoría.
 */
public final class ActorContext {

    private static final ThreadLocal<AuditActor> HOLDER = new ThreadLocal<>();

    private ActorContext() {
    }

    public static void set(AuditActor actor) {
        HOLDER.set(actor);
    }

    public static AuditActor get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
