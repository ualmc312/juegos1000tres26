# Prompt para IA: crear un juego nuevo usando Traductor

## 0) Como usar este documento
Este archivo se debe pasar como contexto a la IA antes de pedirle que cree un juego nuevo.
El objetivo es que la IA reutilice la arquitectura actual y no invierta tiempo en infraestructura innecesaria.

## 1) Objetivo general
1. Implementar logica de juego, no una nueva capa de comunicacion.
2. Mantener separadas estas responsabilidades:
1. Dominio del juego.
2. Traduccion dominio <-> payload.
3. Transporte (API o WebSocket).
3. Reutilizar Traductor, Envio, Recibo y Conexion existentes.

## 2) Que ya existe y para que sirve
Referencias reales del proyecto actual:

1. Patron WebSocket:
1. Backend: PruebaWebSocket
2. Frontend: prueba_websocket.js y prueba_websocket_pantalla.js

2. Patron API + short polling:
1. Backend: SpaceInvadersPruebaService + SpaceInvadersPruebaController
2. Frontend: game.js + space_invaders_scoreboard.html

3. Conclusiones para un juego nuevo:
1. No hay que modificar juegos existentes.
2. Solo hay que usarlos como referencia de patron.

## 3) Componentes de arquitectura explicados en detalle

### 3.1 Enviable
Que hace:
1. Representa un mensaje de dominio del juego.
2. Sabe serializarse con out() y deserializarse con in(...).

Que no hace:
1. No envia por red.
2. No decide rutas ni protocolos.

### 3.2 Envio
Que hace:
1. Convierte Enviable a payload de transporte.

Estado actual:
1. Backend usa Envio.paraStringDesdeOut() para payload String JSON.
2. Frontend usa JsonEnvio.

Que no hace:
1. No decide a quien se envia.
2. No ejecuta reglas de negocio.

### 3.3 Recibo
Que hace:
1. Recibe payload entrante.
2. Extrae comando.
3. Ejecuta el Evento asociado al comando.

Estado actual importante:
1. Backend Recibo<PAYLOAD>: comando no registrado => error.
2. Frontend JsonRecibo: comando no registrado => se descarta sin romper loop.

### 3.4 Evento<PAYLOAD>
Que hace:
1. Contiene la logica de un comando entrante.
2. Recibe payload y ContextoEvento.
3. Puede o no publicar respuesta de negocio.

Regla:
1. Toda logica de comando debe estar aqui, no repartida por controladores.

### 3.5 ContextoEvento 
Que es:
1. Un contexto efimero por mensaje procesado.
2. Se crea dentro de Traductor.procesar(payload).

Ciclo de vida real:
1. Llega payload al Traductor.
2. Traductor crea ContextoEvento nuevo.
3. Traductor llama Recibo.procesar(payload, contexto).
4. El Evento puede llamar contexto.enviar(enviableRespuesta).
5. Traductor consulta contexto.getRespuesta().
6. Si hay respuesta:
1. En procesar(...) se devuelve como Optional/PAYLOAD.
2. En recibirProcesarYResponder() se envia automaticamente por la Conexion.

Reglas del contexto:
1. Solo se permite una respuesta principal por mensaje.
2. Si el evento no llama contexto.enviar(...), se interpreta como operacion sin respuesta principal.

### 3.6 Conexion<PAYLOAD>
Que hace:
1. Implementa el transporte real (API, WebSocket, etc.).
2. Expone enviar(payload) y recibir().
3. Expone metadatos de sala/canal.

Que no hace:
1. No parsea comandos de negocio.
2. No ejecuta reglas del juego.

### 3.7 Traductor<PAYLOAD>
Que hace:
1. Orquesta Envio + Recibo + Conexion.
2. Valida compatibilidad de payload.
3. Expone metodos de alto nivel para enviar/procesar.

Metodos que debe conocer la IA:
1. enviar(enviable): serializa y envia.
2. procesar(payload): ejecuta Recibo y devuelve respuesta opcional.
3. recibirYProcesar(): recibe de Conexion y procesa.
4. recibirProcesarYResponder(): igual que lo anterior, y si hay respuesta principal la envia.

## 4) Flujo completo de una peticion

### 4.1 Flujo API + short polling
1. Front crea Enviable y llama Traductor.enviar(...).
2. Conexion API hace POST al endpoint de eventos.
3. Backend recibe payload y ejecuta Traductor.procesar(payload).
4. Evento aplica logica de negocio.
5. Si hay actualizacion para clientes:
1. Se publica por el canal de salida (updates/cola).
2. Front hace polling a /updates.
3. Front recibe payload y llama Traductor.recibirYProcesar().
4. Evento de frontend actualiza UI.

### 4.2 Flujo WebSocket
1. Front abre canal ws.
2. Front envia por Traductor.enviar(...).
3. Backend procesa payload con Traductor/Recibo/Evento.
4. Backend envia actualizaciones por el canal ws correspondiente.
5. Front mantiene loop recibirYProcesar para actualizar UI en tiempo real.

## 5) Reglas duras (no negociables)
1. No crear otra arquitectura de comunicacion.
2. No duplicar clases base de comunicacion.
3. No reemplazar Traductor por llamadas ad-hoc entre capas.
4. No introducir base de datos si no se pide explicitamente.
5. No romper endpoints ni rutas de otros juegos.
6. No mover logica de comandos al Controller.

## 6) Infraestructura que SI debe reutilizarse
Backend:
1. Traductor: juegos1000tres-backend/src/main/java/com/juegos1000tres/juegos1000tres_backend/comunicacion/Traductor.java
2. Envio: juegos1000tres-backend/src/main/java/com/juegos1000tres/juegos1000tres_backend/comunicacion/Envio.java
3. Recibo: juegos1000tres-backend/src/main/java/com/juegos1000tres/juegos1000tres_backend/comunicacion/Recibo.java
4. Conexiones: ApiConexion y WebSocketConexion
5. Base de juego: juegos1000tres-backend/src/main/java/com/juegos1000tres/juegos1000tres_backend/modelos/Juego.java

Frontend:
1. Core de comunicacion por juego: src/juegos/<Juego>/static/js/comunicacion/core.js
2. Implementaciones: Traductor, JsonEnvio, JsonRecibo, FetchApiConexion, WebSocketConexionNavegador
3. Blueprints Python para rutas HTML y proxy backend

## 7) Como decidir canal para el juego nuevo
Usar WebSocket si:
1. Necesitas baja latencia y push continuo.
2. Hay mucha interaccion simultanea por segundo.

Usar API + polling si:
1. El juego tolera latencia de polling.
2. Quieres endpoints HTTP sencillos y observables.

## 8) Patron minimo de implementacion backend
1. Crear clase de dominio extendiendo Juego.
2. Declarar comandos como constantes.
3. Crear Enviables necesarios.
4. Crear Eventos por comando.
5. En Service:
1. Instanciar Conexion del canal elegido.
2. Instanciar Traductor con Envio.paraStringDesdeOut() y Recibo.paraJsonString().
3. Registrar eventos con Recibo.paraJsonString().conEvento(...).
4. Gestionar loop de recepcion cuando aplique.
6. En Controller exponer minimo:
1. /config si el front necesita URLs/canales.
2. /event para entrada.
3. /updates o endpoint de estado para salida.

## 9) Patron minimo de implementacion frontend
1. Crear vista jugador y opcional vista pantalla.
2. Crear Enviables JS para comandos salientes.
3. Crear Eventos JS para payload entrante.
4. Instanciar Traductor con JsonEnvio + JsonRecibo con comandos registrados + Conexion.
5. Flujo base:
1. conectar
2. enviar en accion de usuario o tick
3. loop recibirYProcesar para UI

## 10) Anti-patrones prohibidos
1. Parsear y rutear comandos en multiples capas fuera de Recibo/Evento.
2. Mezclar logica de negocio pesada dentro de Controller HTTP.
3. Crear un segundo bus de mensajes paralelo para el mismo juego.
4. Copiar un juego completo y renombrar sin modelar comandos/eventos del nuevo dominio.

## 11) Checklist de salida para la IA
1. Compila backend: mvnw test o mvnw -DskipTests package.
2. Levanta frontend y renderiza jugador/pantalla segun aplique.
3. Hay al menos un comando entrante funcional.
4. Hay al menos una actualizacion saliente visible en UI.
5. Envio y recepcion real pasan por Traductor.
6. Cambios acotados al juego nuevo.

## 12) Prompt listo para pegar en otra IA
Copiar y usar este bloque:

"""
Quiero crear un juego nuevo llamado <NOMBRE_JUEGO> en este proyecto.
Debes reutilizar la arquitectura Traductor existente y NO crear infraestructura nueva de comunicacion.

Contexto tecnico obligatorio:
1. Mensajes de salida del juego deben ir como Enviable -> Traductor.enviar(...).
2. Mensajes de entrada deben resolverse por Recibo + Evento.
3. Si una peticion necesita respuesta principal, el Evento debe usar contexto.enviar(...).
4. No mover logica de negocio al Controller.

Requisitos funcionales del juego:
1. <REQ_1>
2. <REQ_2>
3. <REQ_3>

Canal de comunicacion elegido:
1. <WEBSOCKET o API_POLLING>

Entrega minima:
1. Backend del juego (dominio + eventos + service + controller) usando Traductor, Envio y Recibo existentes.
2. Frontend del juego (vista jugador y opcional pantalla) usando core.js existente.
3. Endpoints/config para conectar front-back.
4. Pasos de prueba manual concretos.

Restricciones:
1. No modificar juegos actuales salvo referencia.
2. No crear clases base nuevas de comunicacion.
3. Mantener cambios acotados al juego nuevo.

Validacion final obligatoria:
1. Confirmar explicitamente por que ruta pasan envio y recepcion.
2. Confirmar donde se crea y como se usa ContextoEvento en el flujo.
"""
