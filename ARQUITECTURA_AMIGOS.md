# 🏗️ Arquitectura - Funcionalidad de Amigos

## Diagrama de Componentes

```
┌────────────────────────────────────────────────────────────────┐
│                    NAVEGADOR WEB (localhost:4200)             │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌──────────────────┐                                          │
│  │    App Component │                                          │
│  └────────┬─────────┘                                          │
│           │                                                   │
│           ├──► Lobby Component                                 │
│           │    ┌────────────────────────────────────┐          │
│           │    │  • mostrarAmigos = false           │          │
│           │    │  • abrirAmigos()                   │          │
│           │    │  • cerrarAmigos()                  │          │
│           │    │                                    │          │
│           │    │  Template:                         │          │
│           │    │  <button (click)="abrirAmigos()">  │          │
│           │    │    👥 Amigos                       │          │
│           │    │  </button>                         │          │
│           │    │                                    │          │
│           │    │  <app-amigos-modal                 │          │
│           │    │    *ngIf="mostrarAmigos"           │          │
│           │    │    [usuarioActual]="usuarioActual" │          │
│           │    │    (cerrar)="cerrarAmigos()">      │          │
│           │    │  </app-amigos-modal>               │          │
│           │    └────────────────────────────────────┘          │
│           │                                                   │
│           └──► AmigosModal Component                           │
│                ┌─────────────────────────────────────────┐    │
│                │  • tab: 'amigos' | 'solicitudes'       │    │
│                │  • amigos: Usuario[]                   │    │
│                │  • solicitudesRecibidas: []            │    │
│                │  • solicitudesEnviadas: []             │    │
│                │                                        │    │
│                │  Métodos:                              │    │
│                │  • cargarDatos()                       │    │
│                │  • eliminarAmigo()                     │    │
│                │  • aceptarSolicitud()                 │    │
│                │  • rechazarSolicitud()                │    │
│                │  • enviarSolicitud()                  │    │
│                │  • buscarUsuario()                    │    │
│                └──────────────────┬────────────────────┘    │
│                                   │                         │
│                                   └──► AmigosService        │
│                                        ┌─────────────────┐  │
│                                        │ • httpClient    │  │
│                                        │ • apiBaseUrl    │  │
│                                        │                 │  │
│                                        │ HTTP Methods:   │  │
│                                        │ • enviar()      │  │
│                                        │ • aceptar()     │  │
│                                        │ • rechazar()    │  │
│                                        │ • eliminar()    │  │
│                                        │ • obtener()     │  │
│                                        └────────┬────────┘  │
└────────────────────────────────────────────────┼───────────────┘
                                                 │
                                     HTTP Requests (REST)
                                                 │
┌────────────────────────────────────────────────┼───────────────┐
│          BACKEND (localhost:8080)              │               │
├────────────────────────────────────────────────┼───────────────┤
│                                                                │
│  ┌──────────────────────────────────────────────────────┐     │
│  │ AmigosController                                     │     │
│  │ Base Path: /api/amigos                               │     │
│  │                                                      │     │
│  │ POST   /solicitar                                    │     │
│  │ PUT    /aceptar/{id}                                 │     │
│  │ PUT    /rechazar/{id}                                │     │
│  │ DELETE /{id1}/{id2}                                  │     │
│  │ GET    /mis-amigos/{id}                              │     │
│  │ GET    /solicitudes-recibidas/{id}                   │     │
│  │ GET    /solicitudes-enviadas/{id}                    │     │
│  │ GET    /son-amigos/{id1}/{id2}                       │     │
│  └──────────────────┬─────────────────────────────────┘     │
│                     │                                        │
│  ┌──────────────────▼─────────────────────────────────┐     │
│  │ AmigosService                                      │     │
│  │ @Service @Transactional                            │     │
│  │                                                    │     │
│  │ • enviarSolicitudAmistad()                          │     │
│  │ • aceptarSolicitud()                               │     │
│  │ • rechazarSolicitud()                              │     │
│  │ • eliminarAmistad()                                │     │
│  │ • obtenerAmigos()                                  │     │
│  │ • obtenerSolicitudesRecibidas()                    │     │
│  │ • obtenerSolicitudesEnviadas()                     │     │
│  │ • sonAmigos()                                      │     │
│  └──────┬────────────────────────┬────────────────────┘     │
│         │                        │                          │
│    ┌────▼────┐            ┌──────▼────────┐               │
│    │ SolicitudAmistad    │ Amistad         │               │
│    │ Repository          │ Repository      │               │
│    │                     │                 │               │
│    │ @Query Methods:     │ @Query Methods: │               │
│    │ • findByUsuario     │ • findAmigosDelUsuario()│       │
│    │ • findSolicitud     │ • sonAmigos()   │               │
│    │   Between()         │ • findAmistad   │               │
│    │ • findAllByUsuario()│   Between()     │               │
│    └────┬────────────────┴──────┬──────────┘               │
│         │                       │                          │
│  ┌──────▼──────────────────────▼────────────────────┐     │
│  │              PostgreSQL Database                │     │
│  │  ┌────────────────────────────────────────────┐ │     │
│  │  │ Table: solicitud_amistad                   │ │     │
│  │  │  • id                                      │ │     │
│  │  │  • usuario_solicitante_id                  │ │     │
│  │  │  • usuario_receptor_id                     │ │     │
│  │  │  • estado (PENDIENTE/ACEPTADA/RECHAZADA)   │ │     │
│  │  │  • fecha_creacion                          │ │     │
│  │  │  • fecha_respuesta                         │ │     │
│  │  │  UNIQUE: (usuario_solicitante_id,          │ │     │
│  │  │           usuario_receptor_id)             │ │     │
│  │  └────────────────────────────────────────────┘ │     │
│  │  ┌────────────────────────────────────────────┐ │     │
│  │  │ Table: amistad                             │ │     │
│  │  │  • id                                      │ │     │
│  │  │  • usuario1_id                             │ │     │
│  │  │  • usuario2_id                             │ │     │
│  │  │  • fecha_creacion                          │ │     │
│  │  │  UNIQUE: (usuario1_id, usuario2_id)        │ │     │
│  │  └────────────────────────────────────────────┘ │     │
│  │  ┌────────────────────────────────────────────┐ │     │
│  │  │ Table: usuario (Existente)                 │ │     │
│  │  │  • id                                      │ │     │
│  │  │  • nombre                                  │ │     │
│  │  │  • email                                   │ │     │
│  │  │  • ...otros campos                         │ │     │
│  │  └────────────────────────────────────────────┘ │     │
│  └─────────────────────────────────────────────────┘     │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

## Flujo de Datos - Caso de Uso: Enviar Solicitud

```
┌─────────────────────────────────────────────────────────────────┐
│ Usuario hace click en "Enviar solicitud"                        │
└────────────────────┬────────────────────────────────────────────┘
                     │
        ┌────────────▼─────────────┐
        │ AmigosModal.enviarSolicitud()
        │                          │
        │ • Valida entrada         │
        │ • Llama a servicio       │
        └────────────┬─────────────┘
                     │
        ┌────────────▼────────────────────────┐
        │ AmigosService.enviarSolicitud()     │
        │                                     │
        │ POST /api/amigos/solicitar          │
        │ Body: {usuarioSolicitanteId: 1,    │
        │        usuarioReceptorId: 2}       │
        └────────────┬────────────────────────┘
                     │
        ┌────────────▼────────────────────────────┐
        │ AmigosController.solicitar()            │
        │                                         │
        │ @PostMapping("/solicitar")              │
        │ public ResponseEntity solicitar(...)    │
        └────────────┬────────────────────────────┘
                     │
        ┌────────────▼────────────────────────────┐
        │ AmigosService.enviarSolicitudAmistad()  │
        │                                         │
        │ 1. Validar:                             │
        │    - No solicitud a sí mismo            │
        │    - No ya amigos                       │
        │    - No solicitud pendiente              │
        │                                         │
        │ 2. Crear SolicitudAmistad               │
        │    estado = PENDIENTE                   │
        │    fechaCreacion = now()                │
        │                                         │
        │ 3. Guardar en BD                        │
        └────────────┬────────────────────────────┘
                     │
        ┌────────────▼─────────────────────┐
        │ SolicitudAmistadRepository.save() │
        │                                  │
        │ INSERT INTO solicitud_amistad... │
        └────────────┬─────────────────────┘
                     │
        ┌────────────▼──────────────────────┐
        │ PostgreSQL                         │
        │                                   │
        │ ✓ Registro guardado               │
        │ ✓ Retorna SolicitudAmistad objeto │
        └────────────┬──────────────────────┘
                     │
        ┌────────────▼──────────────────────┐
        │ ResponseEntity (200 OK)           │
        │ Body: {id, usuario..., estado...} │
        └────────────┬──────────────────────┘
                     │
        ┌────────────▼──────────────────────────┐
        │ Frontend recibe respuesta             │
        │                                      │
        │ 1. Actualiza UI                      │
        │ 2. Muestra mensaje "Solicitud enviada" │
        │ 3. Limpia formulario                 │
        │ 4. Recarga lista de solicitudes      │
        └────────────┬──────────────────────────┘
                     │
        ┌────────────▼──────────────────────┐
        │ Usuario ve cambios en la UI       │
        │                                  │
        │ ✓ Solicitud aparece en            │
        │   "Solicitudes enviadas"          │
        └──────────────────────────────────┘
```

## Diagrama de Estados - SolicitudAmistad

```
          ┌─────────────┐
          │  PENDIENTE  │ (Estado inicial)
          └──────┬──────┘
                 │
         ┌───────┴────────┐
         │                │
    ┌────▼────┐      ┌────▼────┐
    │ ACEPTADA │      │RECHAZADA│
    │   (✓)   │      │   (✕)   │
    └─────────┘      └─────────┘
         ▲
         │
  Crear Amistad
```

## Diagrama de Relaciones - Base de Datos

```
┌─────────────────────┐
│      Usuario        │
├─────────────────────┤
│ id (PK)            │
│ nombre             │
│ email              │
│ ...                │
└────────┬────┬──────┘
         │    │
         │    └─────────────────────┐
    ┌────▼────┐                     │
    │ (1:N)   │                 ┌───▼─────────────────────┐
    │         │                 │  SolicitudAmistad       │
    │         │                 ├─────────────────────────┤
    │    ┌────▼────────────────┤ id (PK)                 │
    │    │                     │ usuario_solicitante_id  │
    │    │ usuario_solicitante │ usuario_receptor_id     │
    │    │       ◄─────────────┤ estado (Enum)           │
    │    │                     │ fecha_creacion          │
    │    │                     │ fecha_respuesta         │
    │    │                     │ UNIQUE (sol, rec)       │
    │    └────────────────────┤ FK → Usuario (2x)       │
    │                         └─────────────────────────┘
    └────┬──────────────────────┘
         │
         └─────────────────────┐
                            ┌──▼──────────────────┐
                            │    Amistad          │
                            ├─────────────────────┤
                            │ id (PK)             │
                            │ usuario1_id (FK)    │
                            │ usuario2_id (FK)    │
                            │ fecha_creacion      │
                            │ UNIQUE (u1, u2)    │
                            │ FK → Usuario (2x)   │
                            └─────────────────────┘
```

## Flujo de Navegación - UI

```
┌─────────────────────────────────┐
│   Lobby / Dashboard / Juego     │
│                                 │
│   Botón: 👥 Amigos              │
└────────────┬────────────────────┘
             │ Click
    ┌────────▼──────────────────────────────┐
    │      Modal: Mis Amigos y Solicitudes  │
    ├───────────────────────────────────────┤
    │                                       │
    │  ┌────────────────────────────────┐   │
    │  │ [Mis Amigos] [Solicitudes] [X] │   │
    │  └────────────────────────────────┘   │
    │                                       │
    │  TAB 1: MIS AMIGOS                   │
    │  ├─ Amigo 1 ...................... ✕ │
    │  ├─ Amigo 2 ...................... ✕ │
    │  └─ [+ Añadir amigo]                 │
    │                                       │
    │  TAB 2: SOLICITUDES                  │
    │  ├─ Recibidas:                       │
    │  │  └─ Usuario A ........... ✓ ✕    │
    │  │                                    │
    │  └─ Enviadas:                        │
    │     └─ Usuario B (pendiente)         │
    │                                       │
    └───────────────────────────────────────┘
        │
        ├─► Click "+ Añadir amigo"
        │   ├─► Formulario abre
        │   ├─► Usuario ingresa email
        │   └─► Click "Enviar solicitud"
        │
        ├─► Click ✓ en solicitud recibida
        │   ├─► Solicitud se acepta
        │   ├─► Usuario aparece en "Mis Amigos"
        │   └─► Solicitud desaparece
        │
        └─► Click ✕ en amigo
            ├─► Confirma eliminación
            ├─► Amistad se elimina
            └─► Usuario desaparece
```

## Stack Tecnológico

```
Frontend                Backend              Database
────────                ───────              ────────
Angular 21      ───►    Spring Boot 4.1  ──► PostgreSQL
TypeScript              Java 21              Tables:
RxJS                    Spring Data JPA      • usuario
CSS3                    Spring Web MVC       • amistad
                        Spring Security      • solicitud_amistad
                                             
Communication
────────────
HTTP/REST
Content-Type: application/json
withCredentials: true
```

## Performance

```
Tiempo Promedio de Respuesta:
  GET /mis-amigos/{id}             ~50ms  (lectura simple)
  POST /solicitar                  ~100ms (inserción con validaciones)
  PUT /aceptar/{id}                ~150ms (inserción + update)
  
Bundle Size:
  main.js:  2.08 MB (completo)
  styles:   1.62 KB
  Total:    2.08 MB
  
Compilación:
  ng build: ~11 segundos
```

---

**Última actualización**: 21 de Mayo 2026  
**Versión**: 1.0 (MVP Completo)
