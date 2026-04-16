import { Enviable } from "./comunicacion/Enviable.js";
import { Traductor } from "./comunicacion/Traductor.js";
import { ApiConexion } from "./comunicacion/implementaciones/ApiConexion.js";
import { JsonEnvio } from "./comunicacion/implementaciones/JsonEnvio.js";
import { JsonRecibo } from "./comunicacion/implementaciones/JsonRecibo.js";
import { JuegoConexion } from "./juegos/JuegoConexion.js";
import { TextoEnviable } from "./mensajes/TextoEnviable.js";

class DemoJuegoConexion extends JuegoConexion<string> {
  override async procesarMensajeEntrante(mensaje: Enviable): Promise<void> {
    if (mensaje instanceof TextoEnviable) {
      console.log("Mensaje recibido desde front:", mensaje.texto);
      return;
    }

    console.log("Mensaje recibido desde front (tipo desconocido)");
  }
}

async function main(): Promise<void> {
  const conexion = new ApiConexion();
  const envio = new JsonEnvio();
  const recibo = new JsonRecibo();
  const traductor = new Traductor(conexion, envio, recibo);
  const juego = new DemoJuegoConexion(traductor);

  await conexion.conectar();

  const mensaje = new TextoEnviable("Hola desde Node");
  await juego.enviar(mensaje);
  await traductor.recibirYNotificarJuego(juego, TextoEnviable);

  await conexion.desconectar();
}

main().catch((error: unknown) => {
  const detalle = error instanceof Error ? error.message : String(error);
  console.error("Error en demo frontend:", detalle);
  process.exit(1);
});
