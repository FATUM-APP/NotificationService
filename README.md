# NotificationService

Servicio de notificaciones de Fatum. No habla con los otros servicios: escucha los hechos que ellos
publican en EventBridge y los convierte en notificaciones por usuario.

## Cómo encaja

```
CatalogService ──┐                        ┌── regla: source = fatum.catalogservice ──┐
                 ├── EventBridge (bus) ───┤                                          ├── SQS ──► NotificationService ──► DynamoDB
UserService ─────┘                        └── regla: source = fatum.userservice ────┘                                   │
                                                                                                                          ▼
                                                                                                          GET /notifications (token Cognito)
```

El servicio que publica no sabe que este existe. Si mañana entra un consumidor más (correo, push,
analítica), se suscribe a la misma cola o a una nueva sin tocar a nadie.

## Flujo de un evento

1. EventBridge deja el evento en la cola `fatum-notification-queue` (SQS).
2. `PlatformEventListener` lee el sobre y lo normaliza a `PlatformEvent`.
3. `NotificationRouter` busca la regla que entiende ese `detail-type`.
4. La regla devuelve cero, una o dos notificaciones (cliente y proveedor).
5. `NotificationService` las guarda en DynamoDB con un `id` determinista: si SQS reintenta, se
   sobrescribe la misma fila en vez de duplicarla.

## API

Todas las rutas exigen token de Cognito (`VERIFIED`, `PROFESSIONAL` o `ADMIN`). El usuario sale del
`sub` del token, nunca de un parámetro.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/notifications?limit=20&cursor=...&unreadOnly=false` | Bandeja paginada, lo más nuevo primero |
| GET | `/notifications/unread-count` | Cuántas sin leer |
| PUT | `/notifications/{id}/read` | Marcar una como leída |
| PUT | `/notifications/read-all` | Marcar todas como leídas |
| DELETE | `/notifications/{id}` | Borrar una |

La paginación usa el `id` de la última notificación como cursor: no hay offsets que se desplacen
cuando llega una nueva.

## Variables de entorno

| Variable | Por defecto | Para qué |
|---|---|---|
| `AWS_REGION` | `us-east-1` | Región de DynamoDB y SQS |
| `NOTIFICATION_QUEUE_NAME` | `fatum-notification-queue` | Cola que escucha |
| `NOTIFICATION_TABLE_NAME` | `fatum-notifications` | Tabla de DynamoDB |
| `NOTIFICATION_RETENTION_DAYS` | `60` | Días antes de que el TTL borre la notificación |
| `COGNITO_ISSUER_URI` | placeholder | Issuer del user pool |
| `SECURITY_ENABLED` | `true` | `false` deja la API abierta (solo local) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes separados por coma |
| `DYNAMODB_ENDPOINT` | vacío | Para DynamoDB Local |
| `DYNAMODB_CREATE_TABLE` | `false` | Crea la tabla y el TTL al arrancar |

## Tabla de DynamoDB

| Atributo | Rol |
|---|---|
| `userId` | Partition key (el `sub` de Cognito) |
| `id` | Sort key: `2026-11-12T10:00:00.123Z#uuid` |
| `type`, `title`, `body`, `data`, `read`, `createdAt` | Contenido |
| `expiresAt` | TTL en segundos desde epoch |
| `source`, `eventId` | De dónde vino, para trazabilidad |

Billing `PAY_PER_REQUEST`: no hay capacidad que dimensionar y se escala sola. Si algún día hace falta
consultar por otra clave (por ejemplo, por tipo), se agrega un índice secundario; la tabla no cambia.

## Eventos que entiende

De CatalogService (`source = fatum.catalogservice`):

| detail-type | Quién recibe |
|---|---|
| `ORDER_CREATED` | El proveedor |
| `ORDER_STATUS_CHANGED` | Las dos partes, menos quien provocó el cambio |
| `ORDER_CONFIRMATION_CHANGED` | La parte que todavía tiene que responder |

De UserService (`source = fatum.userservice`): `USER_VERIFICATION_CHANGED`, `USER_BECAME_PROFESSIONAL`,
`PROFESSIONAL_BECAME_CLIENT`, `USER_ACTIVE_STATUS_CHANGED`, `PROFESSIONAL_PRINCIPAL_ADDRESS_CHANGED`.
Todos se notifican al propio usuario.

## Añadir un evento nuevo

1. Crear una clase que implemente `NotificationPolicy`.
2. Declarar en `detailTypes` los tipos que atiende.
3. Devolver las notificaciones con `NotificationFactory.build(...)`.

El router la recoge sola. No hay que tocar el listener ni el controlador.

## Desarrollo local

```bash
# DynamoDB Local
docker run -d -p 8000:8000 amazon/dynamodb-local

DYNAMODB_ENDPOINT=http://localhost:8000 \
DYNAMODB_CREATE_TABLE=true \
SECURITY_ENABLED=false \
mvn spring-boot:run
```

Para probar el consumo sin AWS, basta con mandar a la cola un mensaje con la forma del sobre:

```json
{
  "source": "fatum.catalogservice",
  "detail-type": "ORDER_CREATED",
  "id": "8f1c...",
  "time": "2026-11-12T10:00:00Z",
  "detail": {
    "orderId": "order-1", "clientId": "client-1", "providerId": "provider-1",
    "actorId": "client-1", "catalogItemId": "service-1", "catalogTitle": "Plomería",
    "status": "PENDING", "booking": "2026-11-20T09:00"
  }
}
```

## Despliegue en ECS

Es el mismo patrón que los otros servicios: imagen en ECR, servicio en el cluster `fatum-cluster`,
task role con permisos de `dynamodb:*` sobre la tabla y `sqs:ReceiveMessage/DeleteMessage` sobre la
cola, y el ALB apuntando al puerto 8080. El health check es `/actuator/health`.

La cola necesita una cola de muertos (`fatum-notification-dlq`) con `maxReceiveCount` en 5: si algo
falla siempre, se queda ahí para revisarla en vez de reintentarse para siempre.
