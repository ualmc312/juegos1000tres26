# 🤝 Funcionalidad de Amigos - Frontend

## Descripción General
El frontend implementa una interfaz completa para gestionar amigos, incluyendo un modal estilizado que permite:
- Ver lista de amigos
- Enviar/aceptar/rechazar solicitudes de amistad
- Eliminar amigos
- Ver solicitudes pendientes

## Componentes Creados

### 1. AmigosService (`amigos.service.ts`)
Servicio de comunicación con el backend.

**Métodos:**
- `enviarSolicitud(usuarioSolicitanteId, usuarioReceptorId)`: Envía solicitud de amistad
- `aceptarSolicitud(solicitudId)`: Acepta una solicitud
- `rechazarSolicitud(solicitudId)`: Rechaza una solicitud
- `eliminarAmistad(usuarioId1, usuarioId2)`: Elimina una amistad
- `obtenerAmigos(usuarioId)`: Obtiene lista de amigos
- `obtenerSolicitudesRecibidas(usuarioId)`: Solicitudes pendientes recibidas
- `obtenerSolicitudesEnviadas(usuarioId)`: Solicitudes pendientes enviadas
- `sonAmigos(usuarioId1, usuarioId2)`: Verifica si son amigos

### 2. AmigosModal (`amigos-modal.ts`)
Componente modal con la interfaz de amigos.

**Características:**
- Dos pestañas: "Mis Amigos" y "Solicitudes"
- Lista de amigos con opción de eliminar
- Formulario para añadir nuevos amigos
- Visualización de solicitudes recibidas y enviadas
- Botones para aceptar/rechazar solicitudes
- Mensajes de error y éxito
- Loading spinner durante las peticiones

**Propiedades:**
- `usuarioActual`: Sesión actual del usuario
- `mostrarAmigos`: Booleano para controlar la visibilidad

**Eventos:**
- `cerrar`: Emite cuando el usuario quiere cerrar el modal

### 3. InterfacesService
```typescript
interface Usuario {
  id: number;
  nombre: string;
  email: string;
}

interface SolicitudAmistad {
  id: number;
  usuarioSolicitante: Usuario;
  usuarioReceptor: Usuario;
  estado: 'PENDIENTE' | 'ACEPTADA' | 'RECHAZADA';
  fechaCreacion: string;
}

interface Amistad {
  id: number;
  usuario1: Usuario;
  usuario2: Usuario;
  fechaCreacion: string;
}
```

## Integración en el Lobby

### Cambios en `lobby.ts`
- Importar `AmigosModal`
- Agregar variable `mostrarAmigos`
- Agregar métodos `abrirAmigos()` y `cerrarAmigos()`

### Cambios en `lobby.html`
- Botón "👥 Amigos" en la sección de lobby
- Botón "👥 Amigos" en el sidebar de la sala
- Componente `app-amigos-modal` al final

### Cambios en `lobby.css`
- Estilos para `.botones-principales`
- Estilos para `.sala-botones`

## Estilos (`amigos-modal.css`)

El componente utiliza un diseño futurista coherente con la aplicación:

**Colores:**
- Fondo profundo: `#050510`
- Paneles: `#0d0d26`
- Acentos: Purple (`#a855f7`) y Verde (`#00ff88`)
- Texto: `#e8e8ff`

**Componentes principales:**
- `.amigos-overlay`: Fondo oscuro transparente con blur
- `.amigos-modal`: Modal principal con gradiente y sombra
- `.amigos-tabs`: Sistema de pestañas
- `.amigo-card`: Tarjeta de cada amigo
- `.solicitud-card`: Tarjeta de solicitudes
- `.formulario-anadir`: Formulario para agregar amigos

**Características visuales:**
- Animaciones suaves
- Bordes con brillo
- Gradientes
- Efectos hover
- Scrollbar personalizado

## Flujo de Uso

### Agregar Amigo
1. Usuario abre el modal de amigos
2. Hace clic en "+ Añadir amigo"
3. Ingresa email/nombre del usuario
4. Selecciona el usuario de los resultados
5. Hace clic en "Enviar solicitud"
6. La solicitud aparece en "Solicitudes enviadas"

### Aceptar Solicitud
1. Usuario recibe una solicitud de amistad
2. Va a la pestaña "Solicitudes"
3. Ve la solicitud en "Solicitudes recibidas"
4. Hace clic en ✓ (aceptar)
5. Ambos usuarios aparecen en sus listas de amigos

### Rechazar Solicitud
1. Usuario hace clic en ✕ (rechazar)
2. La solicitud desaparece

### Eliminar Amigo
1. En la pestaña "Mis Amigos"
2. Hace clic en ✕ en la tarjeta del amigo
3. Confirma la acción
4. El amigo se elimina

## Cambios en AuthSession

Se agregó la propiedad opcional `id` al modelo:
```typescript
export interface AuthSession {
  id?: number;
  nombre: string;
  email: string;
  role: 'USER' | 'GUEST';
}
```

## Mejoras Futuras

1. **Búsqueda completa de usuarios**: Implementar búsqueda en el backend
2. **Notificaciones en tiempo real**: Usar WebSocket para notificar nuevas solicitudes
3. **Perfiles de usuario**: Mostrar más información del usuario
4. **Estados online**: Indicar si un amigo está online
5. **Historial de mensajes**: Integración con chat
6. **Bloqios de usuarios**: Sistema de bloqueo

## Responsividad

El modal es completamente responsivo:
- Funciona en dispositivos móviles
- Se ajusta al ancho de la pantalla
- Scrollable en pantallas pequeñas
- Botones accesibles en todos los tamaños

## Seguridad

- Credenciales con `withCredentials: true`
- Validaciones de entrada en componente
- Manejo seguro de errores
- No se exponen datos sensibles

## Testing

Se puede probar de las siguientes formas:

1. **Manual**:
   - Abrir el navegador en `http://localhost:4200`
   - Iniciar sesión
   - Hacer clic en botón "👥 Amigos"
   - Probar funcionalidades

2. **Múltiples usuarios**:
   - Abrir dos navegadores o pestañas con diferentes usuarios
   - Enviar solicitud desde uno
   - Aceptar desde otro
   - Verificar que aparecen como amigos

