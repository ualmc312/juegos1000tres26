package com.juegos1000tres.juegos1000tres_backend.juegos.PruebaWebSocket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.modelos.Juego;

public class PruebaWebSocket extends Juego {

	public static final String COMANDO_ENVIAR_TEXTO = "ENVIAR_TEXTO";
	public static final String COMANDO_TEXTO_GLOBAL = "TEXTO_GLOBAL";
	public static final String COMANDO_ESTADO_PANTALLA = "ESTADO_PANTALLA";

	private final Map<String, RegistroJugador> registrosPorJugador;
	private final List<String> historialGlobal;
	private volatile boolean enCurso;

	public PruebaWebSocket(Traductor<String> conexionJugadores, Traductor<String> conexionPantalla) {
		super(100, true, conexionJugadores, conexionPantalla);
		this.registrosPorJugador = new LinkedHashMap<>();
		this.historialGlobal = new ArrayList<>();
		this.enCurso = false;
	}

	public synchronized void registrarTextoJugador(String jugadorId, String nombreJugador, String texto) {
		String jugadorIdNormalizado = normalizarJugadorId(jugadorId);
		String nombreNormalizado = normalizarNombreJugador(nombreJugador, jugadorIdNormalizado);
		String textoNormalizado = normalizarTexto(texto);

		RegistroJugador registroJugador = this.registrosPorJugador.computeIfAbsent(
				jugadorIdNormalizado,
				(_id) -> new RegistroJugador(jugadorIdNormalizado, nombreNormalizado));

		registroJugador.setNombreJugador(nombreNormalizado);
		registroJugador.getPalabras().add(textoNormalizado);
		this.historialGlobal.add(textoNormalizado);

		traductorJugadores().enviar(new TextoGlobalEnviable(textoNormalizado));
		traductorPantalla().enviar(crearEstadoPantallaEnviable());
	}

	public synchronized List<String> obtenerHistorialGlobal() {
		return List.copyOf(this.historialGlobal);
	}

	public synchronized List<EstadoPantallaEnviable.JugadorPalabrasDTO> obtenerEstadoPantalla() {
		List<EstadoPantallaEnviable.JugadorPalabrasDTO> jugadores = new ArrayList<>();
		for (RegistroJugador registro : this.registrosPorJugador.values()) {
			jugadores.add(new EstadoPantallaEnviable.JugadorPalabrasDTO(
					registro.getJugadorId(),
					registro.getNombreJugador(),
					List.copyOf(registro.getPalabras())));
		}

		return jugadores;
	}

	public synchronized EstadoPantallaEnviable crearEstadoPantallaEnviable() {
		return new EstadoPantallaEnviable(obtenerEstadoPantalla());
	}

	@Override
	public void procesarMensajeEntrante(Enviable mensaje) {
		Objects.requireNonNull(mensaje, "El mensaje entrante es obligatorio");
		throw new UnsupportedOperationException("PruebaWebSocket procesa entradas mediante eventos + payload JSON");
	}

	@Override
	public void iniciar() {
		this.enCurso = true;
	}

	@Override
	public void terminar() {
		this.enCurso = false;
	}

	public boolean isEnCurso() {
		return this.enCurso;
	}

	@SuppressWarnings("unchecked")
	private Traductor<String> traductorJugadores() {
		if (!String.class.equals(this.conexionJugadores.getClasePayload())) {
			throw new IllegalStateException("El traductor de jugadores de PruebaWebSocket debe usar payload String");
		}

		return (Traductor<String>) this.conexionJugadores;
	}

	@SuppressWarnings("unchecked")
	private Traductor<String> traductorPantalla() {
		if (!String.class.equals(this.conexionPantalla.getClasePayload())) {
			throw new IllegalStateException("El traductor de pantalla de PruebaWebSocket debe usar payload String");
		}

		return (Traductor<String>) this.conexionPantalla;
	}

	private String normalizarJugadorId(String jugadorId) {
		if (jugadorId == null || jugadorId.isBlank()) {
			throw new IllegalArgumentException("El campo jugadorId es obligatorio");
		}

		return jugadorId.trim();
	}

	private String normalizarNombreJugador(String nombreJugador, String jugadorId) {
		if (nombreJugador == null || nombreJugador.isBlank()) {
			return "Jugador-" + jugadorId;
		}

		return nombreJugador.trim();
	}

	private String normalizarTexto(String texto) {
		if (texto == null || texto.isBlank()) {
			throw new IllegalArgumentException("El campo texto es obligatorio");
		}

		return texto.trim();
	}

	private static final class RegistroJugador {
		private final String jugadorId;
		private String nombreJugador;
		private final List<String> palabras;

		private RegistroJugador(String jugadorId, String nombreJugador) {
			this.jugadorId = jugadorId;
			this.nombreJugador = nombreJugador;
			this.palabras = new ArrayList<>();
		}

		private String getJugadorId() {
			return jugadorId;
		}

		private String getNombreJugador() {
			return nombreJugador;
		}

		private void setNombreJugador(String nombreJugador) {
			this.nombreJugador = nombreJugador;
		}

		private List<String> getPalabras() {
			return palabras;
		}
	}
}
