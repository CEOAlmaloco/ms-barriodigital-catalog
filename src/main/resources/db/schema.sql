-- Esquema de referencia (Oracle Autonomous).
-- Hibernate crea procedure_types con ddl-auto=update.
-- En una base que ya existe, update no saca el NOT NULL: hay que correr el ALTER de abajo una vez.

CREATE TABLE procedure_types (
    value               VARCHAR2(40)  PRIMARY KEY,
    label               VARCHAR2(120) NOT NULL,
    active              NUMBER(1)     NOT NULL,
    daily_quota         NUMBER(10)    NULL,
    remaining_quota     NUMBER(10)    NULL,
    quota_reset_date    DATE          NOT NULL,
    sla_business_days   NUMBER(10)    NULL
);

-- Null en el cupo significa sin límite. ddl-auto=update no relaja un NOT NULL ya creado.
ALTER TABLE procedure_types MODIFY (daily_quota NULL);
ALTER TABLE procedure_types MODIFY (remaining_quota NULL);

-- Null en el plazo significa sin plazo hasta que el Admin lo defina (EP3-24).
-- En una base ya creada sin la columna, agregar:
-- ALTER TABLE procedure_types ADD (sla_business_days NUMBER(10) NULL);

-- Outbox de auditoría (EP3-68 / mismo patrón EP3-02). Aplicar antes del primer arranque
-- con esta versión: ddl-auto=update no crea el IDENTITY ORDER ni el índice.
CREATE TABLE outbox_events (
    event_id        VARCHAR2(36)  PRIMARY KEY,
    event_sequence  NUMBER GENERATED ALWAYS AS IDENTITY (ORDER),
    aggregate_id    VARCHAR2(80)  NOT NULL,
    event_type      VARCHAR2(60)  NOT NULL,
    payload         CLOB          NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    published_at    TIMESTAMP     NULL,
    attempts        NUMBER        DEFAULT 0 NOT NULL,
    last_error      VARCHAR2(500) NULL
);

CREATE INDEX idx_outbox_pending ON outbox_events (published_at, event_sequence);
