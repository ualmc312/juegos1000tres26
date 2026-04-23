"""
listaJuegos.py
Clase responsable de mantener el catálogo de juegos disponibles.
Cada entrada describe un juego que tiene su propio servidor Flask en src/juegos/<NombreJuego>/app.py
"""

from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class Juego:
    """Representa un juego del catálogo."""
    id: str                  # Identificador único (slug)
    titulo: str              # Nombre para mostrar
    descripcion: str         # Breve descripción
    genero: str              # Género / categoría
    icono: str               # Emoji o ruta de icono
    color: str               # Color de acento de la tarjeta (CSS hex)
    url: str                 # Path relativo para cargar el juego


class ListaJuegos:
    """
    Catálogo centralizado de juegos.
    Para añadir un nuevo juego basta con incluirlo en la lista _juegos.
    """

    _juegos: List[Juego] = field(default_factory=list)

    def __init__(self) -> None:
        self._juegos: List[Juego] = [
            Juego(
                id="space_invaders",
                titulo="Space Invaders",
                descripcion="El clásico arcade de 1978. Defiende la Tierra de las oleadas de alienígenas. ¡Supera el hi-score!",
                genero="Arcade",
                icono="👾",
                color="#00ff88",
                url="/server/space_invaders",
            ),
            Juego(
                id="prueba_websocket",
                titulo="Prueba WebSocket",
                descripcion="Chat minimo para validar comunicacion en tiempo real con Traductor y WebSocket.",
                genero="Pruebas",
                icono="💬",
                color="#53d8fb",
                url="/server/prueba_websocket",
            ),
            Juego(
                id="preguntas",
                titulo="Preguntas Personales",
                descripcion="Rondas de preguntas sobre un jugador elegido al azar. Responde, elige y suma puntos.",
                genero="Social",
                icono="🧠",
                color="#ffcb47",
                url="/server/preguntas",
            ),
        ]

    # ── Consultas ────────────────────────────────────────────────────────────

    def obtener_todos(self) -> List[Juego]:
        """Devuelve la lista completa de juegos."""
        return list(self._juegos)

    def obtener_por_id(self, juego_id: str) -> Optional[Juego]:
        """Devuelve un juego por su id o None si no existe."""
        for juego in self._juegos:
            if juego.id == juego_id:
                return juego
        return None

    def obtener_titulos(self) -> List[str]:
        """Devuelve únicamente los títulos de los juegos."""
        return [j.titulo for j in self._juegos]

    def serializar(self) -> List[dict]:
        """Convierte el catálogo a una lista de dicts (apta para JSON)."""
        return [
            {
                "id": j.id,
                "titulo": j.titulo,
                "descripcion": j.descripcion,
                "genero": j.genero,
                "icono": j.icono,
                "color": j.color,
                "url": j.url,
            }
            for j in self._juegos
        ]
