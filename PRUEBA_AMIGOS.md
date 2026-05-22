# 🤝 Guía de Prueba - Funcionalidad de Amigos

## Requisitos Previos

- Backend corriendo en Docker
- Frontend compilado o en ng serve
- Base de datos PostgreSQL con datos de prueba (al menos 2-3 usuarios)

## Pasos para Probar

### 1. Iniciar el Backend

```bash
cd c:\Users\pepep\repositorios\juegos1000tres26
docker compose up --build -d
```

Verifica que el backend esté corriendo:
```
http://localhost:8080
```

### 2. Iniciar el Frontend

En otra terminal:
```bash
cd c:\Users\pepep\repositorios\juegos1000tres26\juegos1000tres-frontend
ng serve --open
```

O si ya tienes la compilación:
```bash
npm start
```

Abrirá automáticamente `http://localhost:4200`

### 3. Flujo de Prueba Completo

#### Escenario A: Usuario 1 Envía Solicitud a Usuario 2

**Usuario 1:**
1. Inicia sesión como usuario 1 (ej: user1@example.com)
2. Espera a que cargue el dashboard
3. Hace clic en botón "👥 Amigos" (en lobby o en la sala)
4. Se abre el modal de amigos
5. En la pestaña "Mis Amigos" debería estar vacía (inicialmente)
6. Hace clic en "+ Añadir amigo"
7. Ingresa el email de usuario 2 (ej: user2@example.com)
8. Hace clic en "Buscar" o presiona Enter
9. Aparecen los resultados (inicialmente mostrará error de búsqueda no implementada)
10. En la pestaña "Solicitudes" verá las solicitudes enviadas

#### Escenario B: Usuario 2 Acepta la Solicitud

**Usuario 2:**
1. Inicia sesión como usuario 2 en otra ventana/pestaña del navegador
2. Hace clic en "👥 Amigos"
3. Se abre el modal
4. Va a la pestaña "Solicitudes"
5. En "Solicitudes recibidas" aparece la solicitud de usuario 1
6. Hace clic en el botón ✓ (aceptar)
7. Recibe mensaje de éxito
8. Va a "Mis Amigos" y usuario 1 aparece en la lista

**Usuario 1:**
1. Vuelve a su ventana del navegador
2. En "Mis Amigos" aparece usuario 2
3. En "Solicitudes > Solicitudes enviadas" desaparece la solicitud

#### Escenario C: Eliminar Amigo

**Usuario 1:**
1. En el modal de amigos, pestaña "Mis Amigos"
2. Ve a usuario 2 en la lista
3. Hace clic en el botón ✕ de la tarjeta de usuario 2
4. Confirma la eliminación en el diálogo
5. Usuario 2 desaparece de la lista

**Usuario 2:**
1. Vuelve a su ventana del navegador
2. En "Mis Amigos" usuario 1 desaparece
3. (Puede necesitar actualizar la página)

### 4. Flujos Alternativos

#### A. Usuario Rechaza Solicitud

1. Usuario 2 recibe solicitud de usuario 1
2. En lugar de aceptar, hace clic en ✕ (rechazar)
3. Recibe mensaje de éxito
4. La solicitud desaparece

#### B. Usuario Intenta Enviarse Solicitud a Sí Mismo

1. Usuario 1 intenta agregarse a sí mismo
2. Recibe error: "No puedes enviarte una solicitud a ti mismo"

#### C. Usuario Intenta Agregar a Alguien que Ya es Amigo

1. Usuario 1 y Usuario 2 ya son amigos
2. Usuario 1 intenta volver a enviar solicitud
3. Recibe error: "Ya son amigos" o similar

#### D. Solicitud Duplicada

1. Usuario 1 envía solicitud a Usuario 2
2. Usuario 1 intenta enviar otra solicitud al mismo Usuario 2
3. Recibe error: "Ya existe una solicitud pendiente"

## Checklist de Verificación

### Backend API

- [ ] GET `/api/amigos/mis-amigos/1` - Retorna lista de amigos
- [ ] GET `/api/amigos/solicitudes-recibidas/1` - Retorna solicitudes recibidas
- [ ] GET `/api/amigos/solicitudes-enviadas/1` - Retorna solicitudes enviadas
- [ ] POST `/api/amigos/solicitar` - Envía solicitud
- [ ] PUT `/api/amigos/aceptar/1` - Acepta solicitud
- [ ] PUT `/api/amigos/rechazar/1` - Rechaza solicitud
- [ ] DELETE `/api/amigos/1/2` - Elimina amistad
- [ ] GET `/api/amigos/son-amigos/1/2` - Verifica si son amigos

### Frontend UI

- [ ] Modal abre al hacer clic en botón "👥 Amigos"
- [ ] Modal cierra al hacer clic en X
- [ ] Pestaña "Mis Amigos" muestra lista de amigos
- [ ] Pestaña "Solicitudes" muestra solicitudes recibidas y enviadas
- [ ] Botón "+ Añadir amigo" abre formulario
- [ ] Formulario permite ingresar email de usuario
- [ ] Botón ✓ en solicitud recibida acepta
- [ ] Botón ✕ en solicitud recibida rechaza
- [ ] Botón ✕ en amigo elimina amistad
- [ ] Mensajes de éxito/error se muestran correctamente
- [ ] Loading spinner aparece durante peticiones

### Responsive Design

- [ ] Modal se ve bien en escritorio (1920px)
- [ ] Modal se ve bien en tablet (768px)
- [ ] Modal se ve bien en móvil (375px)
- [ ] Los botones son accesibles en todos los tamaños

### Styling

- [ ] Colores coinciden con la paleta de la aplicación
- [ ] Animaciones son suaves
- [ ] No hay parpadeos o desalineación
- [ ] Las fuentes se ven bien (Press Start 2P para títulos, Outfit para UI)

## Debugging

Si algo no funciona, verifica:

1. **Consola del navegador** (F12):
   - Abre "Console" para ver errores JavaScript
   - Abre "Network" para ver llamadas API
   - Verifica que las peticiones a `/api/amigos/*` retornen 200

2. **Logs del backend** (terminal Docker):
   ```bash
   docker compose logs -f backend
   ```
   - Busca errores de SQL
   - Verifica que los datos se insertan correctamente

3. **Base de datos** (PostgreSQL):
   ```sql
   SELECT * FROM solicitud_amistad;
   SELECT * FROM amistad;
   ```
   - Verifica que los datos se guardan

4. **Verificar usuarios de prueba**:
   ```sql
   SELECT id, nombre, email FROM usuario LIMIT 5;
   ```
   - Asegúrate de tener al menos 2 usuarios para probar

## Posibles Mejoras Futuras

1. **Búsqueda de usuarios completa**: Backend endpoint para buscar usuarios
2. **Notificaciones en tiempo real**: WebSocket para nuevas solicitudes
3. **Paginación**: Si hay muchos amigos/solicitudes
4. **Caché**: Almacenar datos en localStorage
5. **Validaciones adicionales**: Email válido, usuario existente, etc.

## Contacto

Si encuentras problemas, contacta al equipo de desarrollo.
