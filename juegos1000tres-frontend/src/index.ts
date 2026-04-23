import { ContextoEvento } from "./comunicacion/ContextoEvento.js";
import { Evento } from "./comunicacion/Evento.js";
import { Traductor } from "./comunicacion/Traductor.js";
import { ApiConexion } from "./comunicacion/implementaciones/ApiConexion.js";
import { JsonEnvio } from "./comunicacion/implementaciones/JsonEnvio.js";
import { JsonRecibo } from "./comunicacion/implementaciones/JsonRecibo.js";
import { TextoEnviable } from "./mensajes/TextoEnviable.js";

class EventoEcoTexto implements Evento<string> {
  hacer(payload: string, contexto: ContextoEvento): void {
    const mensaje = new TextoEnviable();
    mensaje.in(payload);
    contexto.enviar(new TextoEnviable(`Eco: ${mensaje.texto}`));
  }
}

async function main(): Promise<void> {
  const conexion = new ApiConexion();
  const envio = new JsonEnvio();
  const recibo = new JsonRecibo().conEvento("ECO", new EventoEcoTexto());
  const traductor = new Traductor(conexion, envio, recibo);

  await conexion.conectar();

  const peticion = JSON.stringify({ comando: "ECO", texto: "Hola desde Node" });
  await conexion.enviar(peticion);
  const respuesta = await traductor.recibirProcesarYResponder();

  if (respuesta) {
    console.log("Respuesta generada:", respuesta);
  } else {
    console.log("La peticion no genero respuesta de negocio");
  }

  await conexion.desconectar();
}

main().catch((error: unknown) => {
  const detalle = error instanceof Error ? error.message : String(error);
  console.error("Error en demo frontend:", detalle);
  process.exit(1);
});
