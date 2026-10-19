-- Ejecucion MANUAL como script (SQL Developer / SQLcl), en la base usada por catalog.
-- No lo ejecuta Spring ni Hibernate. DDL de Oracle realiza commit implicito.
-- Destino confirmado por el error reportado: BARRIODIGITAL.PROCEDURE_TYPES.
-- No modifica valores, tipos, claves ni las demas restricciones de la tabla.

SELECT SYS_CONTEXT('USERENV', 'DB_NAME') AS database_name,
       SYS_CONTEXT('USERENV', 'SERVICE_NAME') AS service_name,
       SYS_CONTEXT('USERENV', 'SESSION_USER') AS session_user
FROM dual;

SELECT owner, table_name, column_name, nullable
FROM all_tab_columns
WHERE owner = 'BARRIODIGITAL'
  AND table_name = 'PROCEDURE_TYPES'
  AND column_name IN ('DAILY_QUOTA', 'REMAINING_QUOTA')
ORDER BY column_name;

-- Solo altera columnas que todavia son NOT NULL; se puede volver a ejecutar.
DECLARE
    quota_columns PLS_INTEGER;
BEGIN
    SELECT COUNT(*) INTO quota_columns
    FROM all_tab_columns
    WHERE owner = 'BARRIODIGITAL'
      AND table_name = 'PROCEDURE_TYPES'
      AND column_name IN ('DAILY_QUOTA', 'REMAINING_QUOTA');

    IF quota_columns <> 2 THEN
        RAISE_APPLICATION_ERROR(-20001, 'No se encontraron las dos columnas de cupo en el esquema esperado');
    END IF;

    FOR quota_column IN (
        SELECT column_name FROM all_tab_columns
        WHERE owner = 'BARRIODIGITAL'
          AND table_name = 'PROCEDURE_TYPES'
          AND column_name IN ('DAILY_QUOTA', 'REMAINING_QUOTA')
          AND nullable = 'N'
    ) LOOP
        IF quota_column.column_name = 'DAILY_QUOTA' THEN
            EXECUTE IMMEDIATE 'ALTER TABLE BARRIODIGITAL.PROCEDURE_TYPES MODIFY (daily_quota NULL)';
        ELSE
            EXECUTE IMMEDIATE 'ALTER TABLE BARRIODIGITAL.PROCEDURE_TYPES MODIFY (remaining_quota NULL)';
        END IF;
    END LOOP;
END;
/

-- Resultado esperado: ambas columnas con NULLABLE = Y.
SELECT owner, table_name, column_name, nullable
FROM all_tab_columns
WHERE owner = 'BARRIODIGITAL'
  AND table_name = 'PROCEDURE_TYPES'
  AND column_name IN ('DAILY_QUOTA', 'REMAINING_QUOTA')
ORDER BY column_name;
