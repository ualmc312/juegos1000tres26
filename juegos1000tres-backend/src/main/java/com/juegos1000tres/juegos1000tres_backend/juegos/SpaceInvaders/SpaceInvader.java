package com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.modelos.Juego;

public class SpaceInvader extends Juego {

	public static final String COMANDO_ACTUALIZAR_PUNTUACION = "ACTUALIZAR_PUNTUACION";
	public static final String COMANDO_NOTIFICAR_MUERTE = "NOTIFICAR_MUERTE";

	private final Map<UUID, EstadoJugadorInterno> estadoJugadores;
	private final ActualizarPuntuacionEvento actualizarPuntuacionEvento;
	private final NotificarMuerteJugadorEvento notificarMuerteJugadorEvento;
	private volatile boolean enCurso;

	public SpaceInvader(int numeroJugadores, Traductor<?> conexionJugadores, Traductor<?> conexionPantalla) {
		super(numeroJugadores, true, conexionJugadores, conexionPantalla);
		this.estadoJugadores = new ConcurrentHashMap<>();
		this.actualizarPuntuacionEvento = new ActualizarPuntuacionEvento(this);
		this.notificarMuerteJugadorEvento = new NotificarMuerteJugadorEvento(this);
		this.enCurso = false;
	}

	public Recibo<String> registrarEventosEnRecibo(Recibo<String> reciboBase) {
		Objects.requireNonNull(reciboBase, "El recibo base es obligatorio");

		Recibo<String> reciboConPuntuacion = reciboBase.conEvento(
				COMANDO_ACTUALIZAR_PUNTUACION,
				this.actualizarPuntuacionEvento);

		return reciboConPuntuacion.conEvento(COMANDO_NOTIFICAR_MUERTE, this.notificarMuerteJugadorEvento);
	}

	public void registrarJugador(UUID jugadorId, String nombreJugador) {
		UUID id = Objects.requireNonNull(jugadorId, "El id del jugador es obligatorio");

		if (nombreJugador == null || nombreJugador.isBlank()) {
			throw new IllegalArgumentException("El nombre del jugador no puede estar vacio");
		}

		this.estadoJugadores.putIfAbsent(id, new EstadoJugadorInterno(id, nombreJugador.trim()));
	}

	public void actualizarPuntuacion(UUID jugadorId, int puntuacionTotal) {
		if (puntuacionTotal < 0) {
			throw new IllegalArgumentException("La puntuacion no puede ser negativa");
		}

		EstadoJugadorInterno estadoJugador = obtenerJugadorRegistrado(jugadorId);
		estadoJugador.setPuntuacion(puntuacionTotal);
	}

	public void marcarJugadorComoMuerto(UUID jugadorId) {
		EstadoJugadorInterno estadoJugador = obtenerJugadorRegistrado(jugadorId);
		estadoJugador.setMuerto(true);
	}

	public int getPuntuacion(UUID jugadorId) {
		return obtenerJugadorRegistrado(jugadorId).getPuntuacion();
	}

	public boolean haPerdido(UUID jugadorId) {
		return obtenerJugadorRegistrado(jugadorId).isMuerto();
	}

	public Set<UUID> getJugadoresQueHanPerdido() {
		Set<UUID> jugadoresMuertos = new LinkedHashSet<>();
		for (EstadoJugadorInterno estadoJugador : this.estadoJugadores.values()) {
			if (estadoJugador.isMuerto()) {
				jugadoresMuertos.add(estadoJugador.getJugadorId());
			}
		}
		return Set.copyOf(jugadoresMuertos);
	}

	public EstadoJugadoresSpaceInvaders crearEstadoEnviable() {
		List<EstadoJugadorInterno> estadosOrdenados = new ArrayList<>(this.estadoJugadores.values());
		estadosOrdenados.sort(Comparator
				.comparingInt(EstadoJugadorInterno::getPuntuacion)
				.reversed()
				.thenComparing(EstadoJugadorInterno::getNombreJugador));

		List<EstadoJugadoresSpaceInvaders.EstadoJugadorDTO> jugadores = new ArrayList<>();
		for (EstadoJugadorInterno estado : estadosOrdenados) {
			jugadores.add(new EstadoJugadoresSpaceInvaders.EstadoJugadorDTO(
					estado.getJugadorId(),
					estado.getNombreJugador(),
					estado.getPuntuacion(),
					estado.isMuerto()));
		}

		return new EstadoJugadoresSpaceInvaders(jugadores);
	}

	public void publicarEstadoEnPantalla() {
		this.conexionPantalla.enviar(crearEstadoEnviable());
	}

	@Override
	public void procesarMensajeEntrante(Enviable mensaje) {
		Objects.requireNonNull(mensaje, "El mensaje entrante es obligatorio");

		if (!(mensaje instanceof EstadoJugadoresSpaceInvaders estadoRecibido)) {
			throw new IllegalArgumentException("Tipo de mensaje no soportado para SpaceInvader");
		}

		for (EstadoJugadoresSpaceInvaders.EstadoJugadorDTO jugadorDTO : estadoRecibido.getJugadores()) {
			aplicarEstadoJugador(jugadorDTO);
		}
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
		return enCurso;
	}

	private void aplicarEstadoJugador(EstadoJugadoresSpaceInvaders.EstadoJugadorDTO jugadorDTO) {
		if (jugadorDTO == null) {
			return;
		}

		UUID jugadorId = Objects.requireNonNull(jugadorDTO.getJugadorId(), "El jugadorId del estado es obligatorio");
		String nombreJugador = jugadorDTO.getNombreJugador();
		if (nombreJugador == null || nombreJugador.isBlank()) {
			nombreJugador = "Jugador-" + jugadorId;
		}

		this.estadoJugadores.putIfAbsent(jugadorId, new EstadoJugadorInterno(jugadorId, nombreJugador.trim()));

		EstadoJugadorInterno estadoInterno = obtenerJugadorRegistrado(jugadorId);
		estadoInterno.setPuntuacion(jugadorDTO.getPuntuacion());
		estadoInterno.setMuerto(jugadorDTO.isMuerto());
	}

	private EstadoJugadorInterno obtenerJugadorRegistrado(UUID jugadorId) {
		UUID id = Objects.requireNonNull(jugadorId, "El id del jugador es obligatorio");
		EstadoJugadorInterno estadoJugador = this.estadoJugadores.get(id);

		if (estadoJugador == null) {
			throw new IllegalArgumentException("El jugador no esta registrado en la partida");
		}

		return estadoJugador;
	}

	static final class EstadoJugadorInterno {

		private final UUID jugadorId;
		private final String nombreJugador;
		private int puntuacion;
		private boolean muerto;

		EstadoJugadorInterno(UUID jugadorId, String nombreJugador) {
			this.jugadorId = Objects.requireNonNull(jugadorId, "El id del jugador es obligatorio");
			this.nombreJugador = Objects.requireNonNull(nombreJugador, "El nombre del jugador es obligatorio");
			this.puntuacion = 0;
			this.muerto = false;
		}

		UUID getJugadorId() {
			return jugadorId;
		}

		String getNombreJugador() {
			return nombreJugador;
		}

		int getPuntuacion() {
			return puntuacion;
		}

		boolean isMuerto() {
			return muerto;
		}

		void setPuntuacion(int puntuacion) {
			this.puntuacion = puntuacion;
		}

		void setMuerto(boolean muerto) {
			this.muerto = muerto;
		}
	}
}
