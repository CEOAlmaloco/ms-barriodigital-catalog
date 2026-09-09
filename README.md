# ms-barriodigital-catalog

Catálogo de **tipos de trámite** y cupos (BarrioDigital).

Puerto: **8082**

En EP1 solo dejamos el esqueleto. El CRUD de cupos es EP2.

## Arranque

```powershell
.\mvnw.cmd spring-boot:run
```

## Probar

```powershell
curl http://localhost:8082/actuator/health
curl http://localhost:8082/api/catalog/procedures
```

Deberías ver un par de tipos de ejemplo (retiro de escombros, denuncia por ruido).
