## Primera versión de arquitectura de comunicación

Este documento describe cómo está organizada la comunicación entre el juego y los canales de transporte, siguiendo una arquitectura simétrica en backend y frontend.

## 1. Funcionamiento interno

### 1.1 Principio general

La idea principal es separar tres responsabilidades:

1. Lógica del juego.
2. Traducción entre mensajes de dominio y formato de transporte.
3. Transporte físico de datos (API, WebSocket...).

De esta forma, el juego no depende de detalles de red y el canal no depende de reglas de negocio.

### 1.2 Componentes y roles

1. Enviable
: Representa un mensaje de dominio intercambiable entre front y back. Cada mensaje concreto implementa su serialización y deserialización.

2. Envio
: Convierte un Enviable a un payload de transporte.

3. Recibo
: Convierte un payload de transporte en un Enviable.

4. Conexion
: Envía y recibe payloads por un canal concreto. También expone su tipo de comunicación para comprobar homología entre extremos.

5. Traductor
: Orquesta Envio + Recibo + Conexion. Es el único punto que conoce toda la cadena de comunicación y valida que los tres usan el mismo tipo de payload.

6. Juego / JuegoConexion
: Es la lógica de dominio. Para enviar, delega en el Traductor. Para recibir, el Traductor puede notificar al juego cuando llega un mensaje.

### 1.3 Flujo interno de envío

1. El juego crea un Enviable concreto.
2. El juego llama a Traductor.enviar(...).
3. Traductor usa Envio para convertir Enviable a payload.
4. Traductor delega en Conexion para mandar ese payload.

### 1.4 Flujo interno de recepción

1. Conexion recibe un payload del canal.
2. Traductor obtiene ese payload con recibir(...).
3. Traductor usa Recibo para convertir payload a Enviable.
4. Traductor devuelve el mensaje al juego o notifica directamente al juego mediante recibirYNotificarJuego(...).

### 1.5 Homología de canal

Cada Conexion declara su tipo de comunicación (por ejemplo API o WEBSOCKET) y soporta una verificación ComprobarHomologo para asegurar que frontend y backend están hablando por el mismo tipo de canal.

## 2. Cómo se comunican frontend y backend

La arquitectura es simétrica: ambos lados tienen Enviable, Envio, Recibo, Traductor y Conexion.

### 2.1 Comunicación de frontend hacia backend

1. Frontend crea un Enviable de dominio.
2. Frontend usa su Traductor para transformarlo y enviarlo por su Conexion.
3. Backend recibe el payload por su Conexion equivalente.
4. Backend usa su Recibo para reconstruir el Enviable y procesarlo en Juego.

### 2.2 Comunicación de backend hacia frontend

1. Backend genera un Enviable como respuesta o evento de juego.
2. Backend lo envía con su Traductor.
3. Frontend recibe el payload por su Conexion.
4. Frontend reconstruye el Enviable con su Recibo y actualiza estado, UI o flujo de partida.

### 2.3 Compatibilidad de payload

El contrato exige que Envio, Recibo y Conexion usen el mismo tipo de payload dentro de cada lado.

Ejemplo actual:

1. Payload: string JSON.
2. Envio: Enviable -> string JSON.
3. Recibo: string JSON -> Enviable.
4. Conexion: transporta string JSON.

En el futuro se puede cambiar a otro payload (por ejemplo un objeto/protobuf de gRPC) sin cambiar la lógica del juego, solo cambiando implementaciones de Envio, Recibo y Conexion.

## 3. Qué actualizar al crear un nuevo juego

Al añadir un juego nuevo, este es el mínimo recomendado.

### 3.1 Backend

1. Crear clase de juego concreta que extienda Juego.
2. Implementar procesamiento de mensajes entrantes del juego.
3. Crear mensajes Enviable concretos del juego.
4. Crear Envio específico del juego. (Depende)
5. Crear Recibo específico del juego.
6. Crear o reutilizar Conexion según canal (API/WebSocket/u otro).
7. Instanciar Traductor con Conexion + Envio + Recibo del nuevo juego.

### 3.2 Frontend

1. Crear clase de juego concreta (por ejemplo extendiendo JuegoConexion).
2. Crear mensajes Enviable concretos del juego.
3. Crear Envio específico del juego.
4. Crear Recibo específico del juego.
5. Crear o reutilizar Conexion según canal.
6. Instanciar Traductor con Conexion + Envio + Recibo.

### 3.3 Regla de oro

Cada juego debe tener su propia traducción de mensajes de dominio (Envio/Recibo), mientras que la infraestructura de transporte (Conexion) puede reutilizarse si el canal es el mismo.

## 4. Posibles cambios

La versión actual esta pensada para en el caso de API solo desplegar un único endpoint y que sea Recibo el que se encargue de traducirlo a un evento.

Se puede hacer que traductor tenga la posibilidad de que otras clases se suscriban indicando el comando que debe tener el json al que se le tendría que llamar, de esta forma se puede utilizar el mismo Recibo siempre y es más fácil añadir más.

Es decir, recibo o traductor tendrán un HashMap<String,Event> y Event será una interfaz en la que se definira que hacer cuando llegue el comando string.<br>
De forma que la comunicación entre esta capa de envio y el juego será solo en traductor a traves de Juego y Event.

