import { Conexion } from '../conexion';

export interface ApiConexionOpciones {
  resolverPeticion?: (payload: string) => {
    url: string;
    body?: string;
    method?: 'GET' | 'POST';
    headers?: Record<string, string>;
  };
  urlRecepcion?: string;
}

/**
 * Implementación de Conexion usando HTTP/API REST.
 * Traduce los comandos JSON del Traductor a endpoints por sala.
 */
export class ApiConexion implements Conexion<string> {
  private salaId: string;
  private canalSala: string;
  private opciones: ApiConexionOpciones;

  // cuarto parametro opcional: juego -> si se proporciona y canalSala es un host/base,
  // construye endpoints en /api/salas/{salaId}/{juego}/eventos y /actualizaciones
  constructor(salaId: string, canalSala: string, opciones: ApiConexionOpciones = {}, juego?: string) {
    if (!salaId || salaId.trim() === '') {
      throw new Error('El salaId es obligatorio');
    }
    if (!canalSala || canalSala.trim() === '') {
      throw new Error('El canalSala es obligatorio');
    }
    this.salaId = salaId;
    this.opciones = opciones;

    // Si se pasó un juego y canalSala parece un host/base (no contiene '/api/salas/'),
    // construimos los endpoints con el nombre del juego insertado.
    const juegoVal = juego && juego.trim() ? juego.trim() : null;
    if (juegoVal && !canalSala.includes('/api/salas/')) {
      const baseHost = canalSala.replace(/\/?$/, '');
      this.canalSala = `${baseHost}/api/salas/${encodeURIComponent(this.salaId)}/${encodeURIComponent(juegoVal)}/eventos`;
      // si no hay urlRecepcion explícita, la completamos
      if (!this.opciones.urlRecepcion) {
        this.opciones.urlRecepcion = `${baseHost}/api/salas/${encodeURIComponent(this.salaId)}/${encodeURIComponent(juegoVal)}/actualizaciones`;
      }
    } else if (juegoVal && canalSala.includes('/api/salas/')) {
      // si canalSala ya apunta a /api/salas/{sala}/eventos, intentamos insertar juego
      this.canalSala = canalSala.replace(`${encodeURIComponent(this.salaId)}/eventos`, `${encodeURIComponent(this.salaId)}/${encodeURIComponent(juegoVal)}/eventos`);
      if (!this.opciones.urlRecepcion) {
        this.opciones.urlRecepcion = canalSala.replace('/eventos', '/actualizaciones').replace(`${encodeURIComponent(this.salaId)}/eventos`, `${encodeURIComponent(this.salaId)}/${encodeURIComponent(juegoVal)}/actualizaciones`);
      }
    } else {
      this.canalSala = canalSala;
    }
    // opciones quedan como estaban
  }

  conectar(): void {
    // No hace nada en HTTP, la conexión es stateless
  }

  desconectar(): void {
    // No hace nada en HTTP
  }

  enviar(payload: string): void {
    if (!payload || payload.trim() === '') {
      throw new Error('El payload es obligatorio');
    }

    void this.enviarAsincrono(payload);
  }

  recibir(): string | Promise<string> {
    return this.recibirAsincrono();
  }

  getClasePayload(): { name: string } {
    return String;
  }

  getTipoComunicacion(): string {
    return 'HTTP';
  }

  getSalaId(): string {
    return this.salaId;
  }

  getCanalSala(): string {
    return this.canalSala;
  }

  getTipoVerificacion(): string {
    return 'ComprobarHomologo';
  }

  comprobarHomologo(tipoComunicacionFront: string): boolean {
    if (!tipoComunicacionFront || tipoComunicacionFront.trim() === '') {
      return false;
    }
    return this.getTipoComunicacion().toLowerCase() === tipoComunicacionFront.toLowerCase().trim();
  }

  private async enviarAsincrono(payload: string): Promise<void> {
    const peticion = this.opciones.resolverPeticion?.(payload);
    const url = peticion?.url ?? this.canalSala;
    const method = peticion?.method ?? 'POST';
    const headers = {
      'Content-Type': 'application/json',
      ...(peticion?.headers ?? {}),
    };

    const maxAttempts = 3;
    let attempt = 0;
    while (true) {
      attempt++;
      try {
        const response = await fetch(url, {
          method,
          headers,
          body: method === 'GET' ? undefined : (peticion?.body ?? payload),
          credentials: 'include',
        });

        if (!response.ok) {
          const detalle = await leerDetalleRespuesta(response);
          // retry on server errors (5xx) or service unavailable
          if (attempt < maxAttempts && (response.status >= 500 || response.status === 503)) {
            await sleep(150 * attempt);
            continue;
          }

          throw new Error(`Error HTTP al enviar (${response.status}): ${detalle}`);
        }

        return;
      } catch (err) {
        if (attempt >= maxAttempts) {
          // Al final no queremos bloquear el hilo principal por un envío fallido; registrar y relanzar
          console.warn('ApiConexion: fallo al enviar tras reintentos', err);
          throw err;
        }
        await sleep(150 * attempt);
      }
    }
  }

  private async recibirAsincrono(): Promise<string> {
    const url = this.opciones.urlRecepcion ?? this.canalSala;
    const maxAttempts = 3;
    let attempt = 0;

    while (true) {
      attempt++;
      try {
        const response = await fetch(url, {
          method: 'GET',
          headers: {
            'Accept': 'application/json',
          },
          credentials: 'include',
        });

        if (!response.ok) {
          const detalle = await leerDetalleRespuesta(response);
          // Si el servicio no está listo (404, 502, 503) probamos de nuevo
          if (attempt < maxAttempts && (response.status === 404 || response.status === 502 || response.status === 503)) {
            await sleep(120 * attempt);
            continue;
          }
          throw new Error(`Error HTTP al recibir (${response.status}): ${detalle}`);
        }

        return await response.text();
      } catch (err) {
        if (attempt >= maxAttempts) {
          console.warn('ApiConexion: fallo al recibir tras reintentos', err);
          // Devolvemos payload inicial vacío para que el Traductor no se rompa
          return '{}';
        }
        await sleep(120 * attempt);
      }
    }
  }
}

async function leerDetalleRespuesta(response: Response): Promise<string> {
  try {
    return await response.text();
  } catch {
    return 'sin detalle';
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
