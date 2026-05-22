# 🤝 Funcionalidad de Amigos - Backend

## Descripción General
Esta funcionalidad permite a los usuarios enviar, aceptar y rechazar solicitudes de amistad, así como gestionar su lista de amigos.

## Arquitectura

### Entidades JPA

#### 1. EstadoSolicitudAmistad (Enum)
Estados posibles para una solicitud:
- `PENDIENTE`: Esperando respuesta
- `ACEPTADA`: Amistad confirmada
- `RECHAZADA`: Solicitud rechazada

#### 2. SolicitudAmistad
Almacena las solicitudes de amistad entre usuarios.

**Campos:**
- `id`: Identificador único
- `usuarioSolicitante`: El usuario que envía la solicitud (FK a Usuario)
- `usuarioReceptor`: El usuario que recibe la solicitud (FK a Usuario)
- `estado`: Estado actual (PENDIENTE, ACEPTADA, RECHAZADA)
- `fechaCreacion`: Timestamp de creación
- `fechaRespuesta`: Timestamp de cuando fue aceptada/rechazada

**Restricciones:**
- Unique constraint en (usuario_solicitante_id, usuario_receptor_id)

#### 3. Amistad
Almacena las amistades confirmadas.

**Campos:**
- `id`: Identificador único
- `usuario1`: Primer usuario
- `usuario2`: Segundo usuario
- `fechaCreacion`: Timestamp de creación

**Restricciones:**
- Unique constraint en (usuario1_id, usuario2_id)
- Los usuarios se ordenan automáticamente por ID para evitar duplicados

### Repositorios

#### SolicitudAmistadRepository
Métodos disponibles:
- `findByUsuarioReceptorAndEstado()`: Solicitudes pendientes recibidas
- `findByUsuarioSolicitanteAndEstado()`: Solicitudes pendientes enviadas
- `findSolicitudBetween()`: Verifica si existe solicitud entre dos usuarios
- `findAllByUsuario()`: Todas las solicitudes de un usuario

#### AmistadRepository
Métodos disponibles:
- `findAmigosDelUsuario()`: Obtiene todos los amigos de un usuario
- `sonAmigos()`: Verifica si dos usuarios son amigos
- `findAmistadBetween()`: Obtiene la amistad entre dos usuarios

### Service

#### AmigosService
Lógica de negocio centralizada.

**Métodos principales:**
- `enviarSolicitudAmistad()`: Crear nueva solicitud
- `aceptarSolicitud()`: Aceptar y crear amistad
- `rechazarSolicitud()`: Rechazar solicitud
- `eliminarAmistad()`: Eliminar amistad existente
- `obtenerAmigos()`: Lista de amigos de un usuario
- `obtenerSolicitudesRecibidas()`: Solicitudes pendientes recibidas
- `obtenerSolicitudesEnviadas()`: Solicitudes pendientes enviadas
- `sonAmigos()`: Verificación de amistad

**Validaciones:**
- No permitir solicitudes a uno mismo
- No permitir solicitud si ya son amigos
- No permitir solicitud duplicada pendiente
- Verificar que usuarios existan en base de datos

## API REST

Base URL: `/api/amigos`

### 1. Enviar Solicitud de Amistad
```
POST /api/amigos/solicitar
Content-Type: application/json

{
  "usuarioSolicitanteId": 1,
  "usuarioReceptorId": 2
}
```

**Respuesta (201 Created):**
```json
{
  "id": 1,
  "usuarioSolicitante": {
    "id": 1,
    "nombre": "Juan",
    "email": "juan@example.com"
  },
  "usuarioReceptor": {
    "id": 2,
    "nombre": "María",
    "email": "maria@example.com"
  },
  "estado": "PENDIENTE",
  "fechaCreacion": "2024-01-15T10:30:00"
}
```

### 2. Aceptar Solicitud
```
PUT /api/amigos/aceptar/1
```

**Respuesta (200 OK):**
```json
{
  "id": 1,
  "usuario1": {
    "id": 1,
    "nombre": "Juan",
    "email": "juan@example.com"
  },
  "usuario2": {
    "id": 2,
    "nombre": "María",
    "email": "maria@example.com"
  },
  "fechaCreacion": "2024-01-15T10:30:00"
}
```

### 3. Rechazar Solicitud
```
PUT /api/amigos/rechazar/1
```

**Respuesta (200 OK):**
```json
{
  "id": 1,
  "usuarioSolicitante": {...},
  "usuarioReceptor": {...},
  "estado": "RECHAZADA",
  "fechaCreacion": "2024-01-15T10:30:00"
}
```

### 4. Eliminar Amistad
```
DELETE /api/amigos/1/2
```

**Respuesta (204 No Content)**

### 5. Obtener Lista de Amigos
```
GET /api/amigos/mis-amigos/1
```

**Respuesta (200 OK):**
```json
[
  {
    "id": 2,
    "nombre": "María",
    "email": "maria@example.com"
  },
  {
    "id": 3,
    "nombre": "Pedro",
    "email": "pedro@example.com"
  }
]
```

### 6. Obtener Solicitudes Recibidas
```
GET /api/amigos/solicitudes-recibidas/1
```

**Respuesta (200 OK):**
```json
[
  {
    "id": 1,
    "usuarioSolicitante": {
      "id": 4,
      "nombre": "Carlos",
      "email": "carlos@example.com"
    },
    "usuarioReceptor": {
      "id": 1,
      "nombre": "Juan",
      "email": "juan@example.com"
    },
    "estado": "PENDIENTE",
    "fechaCreacion": "2024-01-15T10:30:00"
  }
]
```

### 7. Obtener Solicitudes Enviadas
```
GET /api/amigos/solicitudes-enviadas/1
```

**Respuesta (200 OK):** Mismo formato que solicitudes recibidas

### 8. Verificar si Son Amigos
```
GET /api/amigos/son-amigos/1/2
```

**Respuesta (200 OK):**
```json
{
  "sonAmigos": true
}
```

## Flujo de Uso

### Scenario: Usuario A agrega a Usuario B como amigo

1. **Usuario A envía solicitud:**
   ```
   POST /api/amigos/solicitar
   {"usuarioSolicitanteId": 1, "usuarioReceptorId": 2}
   ```
   → Crea SolicitudAmistad en estado PENDIENTE

2. **Usuario B recibe notificación (ver lista de solicitudes):**
   ```
   GET /api/amigos/solicitudes-recibidas/2
   ```
   → Devuelve la solicitud de Usuario A

3. **Usuario B acepta solicitud:**
   ```
   PUT /api/amigos/aceptar/1
   ```
   → Crea Amistad y cambia estado a ACEPTADA

4. **Ambos pueden verificar amistad:**
   ```
   GET /api/amigos/mi-amigos/1
   ```
   → Devuelve Usuario B en la lista

5. **Para eliminar amistad:**
   ```
   DELETE /api/amigos/1/2
   ```
   → Elimina la Amistad

## Manejo de Errores

Todos los endpoints devuelven códigos de error apropiados:

- `400 Bad Request`: 
  - Usuario no encontrado
  - Ya son amigos
  - Solicitud duplicada pendiente
  - No puedes enviar solicitud a ti mismo
  
- `404 Not Found`: Solicitud no encontrada

- `500 Internal Server Error`: Error inesperado del servidor

Formato de error:
```json
{
  "error": "Descripción del error"
}
```

## Base de Datos

### Tablas creadas automáticamente por JPA

- `solicitudes_amistad`: Solicitudes de amistad
- `amistades`: Amistades confirmadas

Las migraciones se ejecutan automáticamente al iniciar Spring Boot.

## Próximos Pasos (Frontend)

Para completar la funcionalidad, el frontend debe:
1. Crear interfaz para enviar solicitudes
2. Mostrar lista de solicitudes pendientes
3. Implementar botones para aceptar/rechazar
4. Mostrar lista de amigos
5. Implementar opción para eliminar amigos
6. Integrar notificaciones (WebSocket) para nuevas solicitudes

