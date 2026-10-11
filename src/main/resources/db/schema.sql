-- Esquema de referencia (Oracle Autonomous).
-- Hibernate crea o actualiza procedure_types con ddl-auto=update.

CREATE TABLE procedure_types (
    value             VARCHAR2(40)  PRIMARY KEY,
    label             VARCHAR2(120) NOT NULL,
    active            NUMBER(1)     NOT NULL,
    daily_quota       NUMBER(10)    NOT NULL,
    remaining_quota   NUMBER(10)    NOT NULL,
    quota_reset_date  DATE          NOT NULL
);
