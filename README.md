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
| `quota_reset_date` | Día del contador, en UTC. No sale en el JSON. |

Al arrancar, si falta una fila de esos seis códigos, se inserta. Si ya existe, no se toca.

`GET /api/catalog/procedures` lee `procedure_types` en cada llamada (EP1.5-10). No hay lista fija en el controlador: un tipo nuevo guardado en la tabla aparece en el siguiente GET sin reiniciar.

## Alta (EP1.5-11)

`POST /api/catalog/procedures` crea un tipo. Solo Admin: el header `X-User-Roles` tiene que traer `Admin` (lo pone el BFF después de validar el JWT). Otro rol, o sin header, responde **403**.

```json
{ "value": "semaforo", "label": "Semáforo en mal estado", "active": true }
```

`active` es opcional y vale `true` si se omite. `value` va en minúsculas, números, guion o guion bajo, hasta 40 caracteres. Si ese código ya está en la tabla, responde **409** y no modifica la fila. El alta es un insert: dos POST simultáneos con el mismo `value` no se pisan. `value` o `label` vacíos, o un `value` que no cumple el formato, responden **400**.

El cupo no se manda en este POST. La fila nace con el mismo cupo inicial del seed (10) y `remaining_quota` igual a ese número. La respuesta es **201** con la misma forma que un ítem del GET.

## Edición (EP1.5-12)

`PUT /api/catalog/procedures/{value}` reemplaza etiqueta, activo y cupo. El código va en la URL y no se puede cambiar: si el cuerpo trae otro `value`, responde **400**. Solo Admin, con el mismo header `X-User-Roles`. Otro rol responde **403**. Si el código no está en la tabla, **404**.

```json
{ "label": "Semáforo intermitente", "active": false, "dailyQuota": 4 }
```

Los tres campos son obligatorios. `dailyQuota` es un entero de 1 o más. Lo ya usado en el día se conserva: si el cupo nuevo no alcanza para eso, `remainingQuota` queda en 0. La respuesta es **200** con la misma forma que un ítem del GET.

## Cupo del día (EP1.5-16)

`dailyQuota` lo cambia el Admin con el PUT de arriba. `remainingQuota` se repone solo: en el primer GET (o PUT) de un día nuevo, si `quota_reset_date` no es hoy en UTC, pasa a valer `dailyQuota` y se guarda la fecha. Un segundo uso el mismo día no vuelve a llenar el cupo. No hay job ni consulta a requests.

El JSON de GET, POST y PUT trae `dailyQuota` y `remainingQuota`. `quotaResetDate` se queda en la tabla.

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
