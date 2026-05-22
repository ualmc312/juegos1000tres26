# 🚀 Quick Start - Funcionalidad de Amigos

## Prueba Rápida (5 minutos)

### Terminal 1: Backend
```bash
cd c:\Users\pepep\repositorios\juegos1000tres26
docker compose up --build -d
```

### Terminal 2: Frontend
```bash
cd c:\Users\pepep\repositorios\juegos1000tres26\juegos1000tres-frontend
ng serve
```

Abre: http://localhost:4200

### En el Navegador

1. **Inicia sesión** como usuario 1
2. Haz clic en botón **"👥 Amigos"** (arriba a la derecha)
3. Se abre el modal de amigos
4. Haz clic en **"+ Añadir amigo"**
5. Ingresa el email de otro usuario (ej: user2@example.com)
6. Haz clic en **"Enviar solicitud"**

### Segundo Usuario (Nueva pestaña)

1. Inicia sesión como usuario 2 en una **nueva pestaña** del navegador
2. Haz clic en **"👥 Amigos"**
3. Ve a la pestaña **"Solicitudes"**
4. Verás la solicitud de usuario 1
5. Haz clic en **✓** para aceptar
6. Ambos usuarios aparecen como amigos

## Troubleshooting Rápido

| Problema | Solución |
|----------|----------|
| Backend no corre | `docker ps` - Verifica que contenedor esté activo |
| API retorna 401 | Reinicia sesión o verifica JWT token |
| Modal no carga amigos | Abre console (F12) y mira Network tab |
| Estilos rotos | Ejecuta `ng build` nuevamente |
| Error de CORS | Verifica `application.properties` (localhost:4200 debe estar permitido) |

## Archivos Importantes

| Archivo | Propósito |
|---------|-----------|
| [RESUMEN_AMIGOS.md](RESUMEN_AMIGOS.md) | Resumen técnico completo |
| [PRUEBA_AMIGOS.md](PRUEBA_AMIGOS.md) | Guía de testing detallada |
| [AMIGOS_FRONTEND.md](AMIGOS_FRONTEND.md) | Documentación del frontend |
| [AMIGOS_BACKEND.md](AMIGOS_BACKEND.md) | Documentación del backend (si existe) |

## Endpoints Disponibles

```
POST   http://localhost:8080/api/amigos/solicitar
PUT    http://localhost:8080/api/amigos/aceptar/{id}
PUT    http://localhost:8080/api/amigos/rechazar/{id}
DELETE http://localhost:8080/api/amigos/{id1}/{id2}
GET    http://localhost:8080/api/amigos/mis-amigos/{id}
GET    http://localhost:8080/api/amigos/solicitudes-recibidas/{id}
GET    http://localhost:8080/api/amigos/solicitudes-enviadas/{id}
GET    http://localhost:8080/api/amigos/son-amigos/{id1}/{id2}
```

## Testear vía API (Postman/Curl)

```bash
# Obtener amigos de usuario con ID 1
curl http://localhost:8080/api/amigos/mis-amigos/1

# Enviar solicitud (usuario 1 -> usuario 2)
curl -X POST http://localhost:8080/api/amigos/solicitar \
  -H "Content-Type: application/json" \
  -d "{\"usuarioSolicitanteId\": 1, \"usuarioReceptorId\": 2}"

# Aceptar solicitud
curl -X PUT http://localhost:8080/api/amigos/aceptar/1
```

## Demo en Video (Pasos)

1. Inicia backend y frontend
2. Login con usuario1
3. Click "👥 Amigos" → Modal abre
4. Click "+ Añadir amigo"
5. Ingresa email usuario2
6. Click "Enviar solicitud"
7. Login usuario2 (otra pestaña)
8. Click "👥 Amigos" → Ir a "Solicitudes"
9. Click ✓ para aceptar
10. Ambos ven el otro en "Mis Amigos"

## Logs para Debugging

Ver logs del backend:
```bash
docker compose logs -f backend
```

Ver logs del frontend (consola del navegador):
```
F12 → Console Tab
```

## Estado del Sistema

✅ Backend compilado y corriendo en Docker  
✅ Frontend compilado y servido en localhost:4200  
✅ Base de datos con usuarios de prueba  
✅ Todos los endpoints implementados  
✅ Modal UI completamente funcional  

## ¿Qué Sigue?

1. Probar flujos completos
2. Verificar que datos se guardan en BD
3. Reportar cualquier error o comportamiento inesperado
4. (Opcional) Implementar búsqueda de usuarios backend
5. (Opcional) Agregar notificaciones WebSocket

---

**Duración esperada de prueba**: 5-10 minutos  
**Complejidad**: Baja (todo está implementado)  
**Riesgo**: Bajo (sistema aislado, datos de prueba)
