package cl.duoc.barriodigital.catalog.messaging;

/** Envío confirmado a audit.timeline. */
public interface AuditKafkaSender {

    void sendConfirmed(String topic, String key, String json) throws Exception;
}
