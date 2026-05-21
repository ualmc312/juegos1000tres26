// Clases generales
export { Enviable } from './enviable';
export { Envio } from './envio';
export { Recibo } from './recibo';
export type { Evento } from './evento';
export type { Conexion } from './conexion';
export { Traductor } from './traductor';

// Implementaciones
export { ApiConexion } from './implementaciones/api-conexion';
export { WebSocketConexion } from './implementaciones/websocket-conexion';
export { P2PConexion } from './implementaciones/p2p-conexion';
