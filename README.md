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
| `daily_quota` | Cupo del día. Null es sin límite. El seed y el POST lo dejan en null. |
| `remaining_quota` | Cupo que queda. Null si no hay límite. |
| `quota_reset_date` | Día del contador, en America/Santiago. No sale en el JSON. |

Al arrancar, si falta una fila de esos seis códigos, se inserta. Si ya existe, no se toca.

`GET /api/catalog/procedures` lee `procedure_types` en cada llamada (EP1.5-10). No hay lista fija en el controlador: un tipo nuevo guardado en la tabla aparece en el siguiente GET sin reiniciar.

## Alta (EP1.5-11)

`POST /api/catalog/procedures` crea un tipo. Solo Admin: el header `X-User-Roles` tiene que traer `Admin` (lo pone el BFF después de validar el JWT). Otro rol, o sin header, responde **403**.

```json
{ "value": "semaforo", "label": "Semáforo en mal estado", "active": true }
```

`active` es opcional y vale `true` si se omite. `value` va en minúsculas, números, guion o guion bajo, hasta 40 caracteres. Si ese código ya está en la tabla, responde **409** y no modifica la fila. El alta es un insert: dos POST simultáneos con el mismo `value` no se pisan. `value` o `label` vacíos, o un `value` que no cumple el formato, responden **400**.

El cupo no se manda en este POST. La fila nace con `daily_quota` y `remaining_quota` en null: sin límite, hasta que el Admin lo ponga con el PUT. La respuesta es **201** con la misma forma que un ítem del GET.

## Edición (EP1.5-12)

`PUT /api/catalog/procedures/{value}` reemplaza etiqueta, activo y cupo. El código va en la URL y no se puede cambiar: si el cuerpo trae otro `value`, responde **400**. Solo Admin, con el mismo header `X-User-Roles`. Otro rol responde **403**. Si el código no está en la tabla, **404**.

```json
{ "label": "Semáforo intermitente", "active": false, "dailyQuota": 4 }
```

`label` y `active` son obligatorios. `dailyQuota` es opcional: si no viene, el tipo pasa a sin límite (`null`). Si viene, es un entero de 1 o más. Lo ya usado en el día se conserva cuando había límite: si el cupo nuevo no alcanza para eso, `remainingQuota` queda en 0. El frontend tiene que mandar siempre `dailyQuota` cuando quiere conservar el límite; si lo omite, lo saca. La respuesta es **200** con la misma forma que un ítem del GET.

## Cupo del día (EP1.5-16)

`dailyQuota` lo cambia el Admin con el PUT de arriba. Null es sin límite y `reserve` no descuenta ni responde 409. Si hay número, `remainingQuota` se repone solo: en el primer GET (o PUT) de un día nuevo, si `quota_reset_date` no es hoy en America/Santiago, pasa a valer `dailyQuota` y se guarda la fecha. Un segundo uso el mismo día no vuelve a llenar el cupo. No hay job ni consulta a requests.

El JSON de GET, POST y PUT trae `dailyQuota` y `remainingQuota`. `quotaResetDate` se queda en la tabla.

## Reservar y liberar (EP1.5-17)

El BFF llama estos dos POST. No llevan body y no miran `X-User-Roles`: catalog confía en la red interna.

```
POST /api/catalog/procedures/{value}/reserve
POST /api/catalog/procedures/{value}/release
```

`reserve` aplica el reset del día y después descuenta 1. Si después del reset no queda cupo, responde **409** y no deja el contador en negativo. Si `dailyQuota` es null (sin límite), responde **200** y no descuenta. `release` suma 1 sin pasar de `dailyQuota` (una compensación repetida no infla el cupo) y nunca responde 409; sin límite tampoco cambia el contador. Si el código no existe, **404**.

Los dos corren en una transacción con candado de escritura sobre la fila, así dos reservas a la vez no descuentan el mismo cupo. La respuesta **200** tiene la misma forma que un ítem del GET.

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

Si la tabla ya existía con `daily_quota` y `remaining_quota` en NOT NULL, Hibernate no se los saca. Hay que correrlo una vez en Oracle:

```sql
ALTER TABLE procedure_types MODIFY (daily_quota NULL);
ALTER TABLE procedure_types MODIFY (remaining_quota NULL);
```

Las filas que el seed viejo dejó en 10 siguen en 10. Un tipo nuevo, o un PUT sin `dailyQuota`, queda sin límite.

Para una base antigua del esquema `BARRIODIGITAL`, `src/main/resources/db/allow-unlimited-quota.sql` prepara la comprobación y el ajuste manual de ambas columnas, solo si todavía son NOT NULL. Ejecútalo como script en la misma base que usa catalog; al terminar, las dos columnas deben mostrar `NULLABLE = Y`. No se ejecuta automáticamente al arrancar y el DDL de Oracle realiza commit implícito. No sustituye valores ni elimina filas.

Los errores de persistencia devuelven un 500 genérico; el detalle de Oracle/Hibernate se registra únicamente en el servidor. Los errores de negocio 400/403/404/409 conservan sus mensajes controlados. La respuesta de error predeterminada no incluye mensajes internos, stack traces, excepciones ni errores de binding.

```sql
SELECT value, label, active, daily_quota, remaining_quota, quota_reset_date
FROM procedure_types
ORDER BY value;
```

## Git

`.gitignore` cubre `target/`, `.env` y el wallet. Los secretos solo van en `.env`.
