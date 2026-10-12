# ms-barriodigital-catalog

Catálogo de **tipos de trámite** (BarrioDigital).

Puerto: **8082**

## Persistencia (EP1.5-09)

Oracle Autonomous, el mismo patrón que `ms-barriodigital-requests`: `.env` local, wallet fuera del repo, Hibernate con `ddl-auto: update`.

La tabla es `procedure_types`. El SQL de referencia está en `src/main/resources/db/schema.sql`.

| Columna | Qué es |
|---|---|
| `value` | Código (`bache`, `alumbrado`, `basura`, `agua`, `ruido`, `otro`). Es la clave y no se cambia. |
| `label` | Texto visible. |
| `active` | Si el tipo se ofrece. El seed lo deja en true. |
| `daily_quota` | Cupo del día. El seed usa 10. |
| `remaining_quota` | Cupo que queda. El seed lo iguala a `daily_quota`. |
| `quota_reset_date` | Día del contador, en UTC. El reset perezoso es EP1.5-16. |

Al arrancar, si falta una fila de esos seis códigos, se inserta. Si ya existe, no se toca.

`GET /api/catalog/procedures` lee `procedure_types` en cada llamada (EP1.5-10). No hay lista fija en el controlador: un tipo nuevo guardado en la tabla aparece en el siguiente GET sin reiniciar. El POST que lo crea es EP1.5-11.

## Arranque

1. Copiá `.env.example` a `.env` y completá Oracle (podés copiar los valores de requests).
2. El wallet queda **fuera** del repo.

```powershell
copy .env.example .env
.\mvnw.cmd spring-boot:run
```

En el log tiene que aparecer `Oracle OK: pool conectado` y `Catalogo: seed de tipos de tramite`.

```powershell
curl http://localhost:8082/actuator/health
```

En Oracle:

```sql
SELECT value, label, active, daily_quota, remaining_quota, quota_reset_date
FROM procedure_types
ORDER BY value;
```

## Git

`.gitignore` cubre `target/`, `.env` y el wallet. Los secretos solo van en `.env`.
