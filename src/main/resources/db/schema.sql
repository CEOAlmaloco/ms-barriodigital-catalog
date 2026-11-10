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
