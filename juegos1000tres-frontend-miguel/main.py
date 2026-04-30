"""
main.py  –  Servidor Flask principal · Arcade Hub
================================================================
Arranca el menú de selección de juego en http://localhost:8080

Estructura de archivos que espera:
    src/
    ├── menu/
    │   └── vistaSeleccionJuego/
    │       ├── seleccionJuego.html   ← template Jinja2
    │       ├── seleccionJuego.css    ← servido como estático
    │       └── seleccionJuego.js     ← servido como estático
    │
    └── juegos/
        └── SpaceInvaders/
            └── app.py                ← servidor independiente del juego

Uso:
    python main.py
    → http://localhost:8080
"""

import os
import socket

from flask import Flask, render_template, jsonify, send_from_directory, request

# Ajustamos las rutas de templates y static al directorio de la vista
BASE_DIR      = os.path.dirname(os.path.abspath(__file__))
TEMPLATE_DIR  = os.path.join(BASE_DIR, "src", "menu", "vistaSeleccionJuego")
STATIC_DIR    = os.path.join(BASE_DIR, "src", "menu", "vistaSeleccionJuego")
PRUEBAS_DIR   = os.path.join(BASE_DIR, "src", "pruebas")
SPACE_INVADERS_DIR = os.path.join(BASE_DIR, "src", "juegos", "SpaceInvaders")
PRUEBA_WEBSOCKET_DIR = os.path.join(BASE_DIR, "src", "juegos", "PruebaWebSocket")
PREGUNTAS_DIR = os.path.join(BASE_DIR, "src", "juegos", "Preguntas")

# Importamos el catálogo de juegos
import sys
sys.path.insert(0, TEMPLATE_DIR)
sys.path.insert(0, PRUEBAS_DIR)
sys.path.insert(0, SPACE_INVADERS_DIR)
sys.path.insert(0, PRUEBA_WEBSOCKET_DIR)
sys.path.insert(0, PREGUNTAS_DIR)
from listaJuegos import ListaJuegos  # noqa: E402
from sala_mentira import SalaMentiraPruebas  # noqa: E402
from space_invaders_blueprint import create_space_invaders_blueprint  # noqa: E402
from prueba_websocket_blueprint import create_prueba_websocket_blueprint  # noqa: E402
from preguntas_blueprint import create_preguntas_blueprint  # noqa: E402

# ── Configuración Flask ───────────────────────────────────────────────────────
app = Flask(
    __name__,
    template_folder=TEMPLATE_DIR,
    static_folder=STATIC_DIR,
    static_url_path="/static_menu",
)
app.config["SECRET_KEY"] = os.getenv("ARCADE_HUB_SECRET", "arcade-hub-dev-secret")

catalogo = ListaJuegos()

# ── Blueprints de Juegos ──────────────────────────────────────────────────────
space_invaders_bp = create_space_invaders_blueprint(BASE_DIR)
prueba_websocket_bp = create_prueba_websocket_blueprint(BASE_DIR)
preguntas_bp = create_preguntas_blueprint(BASE_DIR)

# Registramos el servidor interno del juego
app.register_blueprint(space_invaders_bp, url_prefix="/server/space_invaders")
app.register_blueprint(prueba_websocket_bp, url_prefix="/server/prueba_websocket")
app.register_blueprint(preguntas_bp, url_prefix="/server/preguntas")

# Sala de mentira para pruebas manuales (aislada y facil de eliminar)
sala_mentira_pruebas = SalaMentiraPruebas(BASE_DIR)
app.register_blueprint(
    sala_mentira_pruebas.crear_blueprint(),
    url_prefix=SalaMentiraPruebas.URL_PREFIX,
)


# ── Rutas del Hub (Menú Principal) ───────────────────────────────────────────

@app.route("/", strict_slashes=False)
@app.route("/juego/<juego_id>", strict_slashes=False)
def index(juego_id=None):
    """
    Renderiza la vista principal del Hub.
    Si se incluye juego_id, el frontend lo detectará y lo cargará automáticamente.
    """
    juegos = catalogo.obtener_todos()
    juegos_json = catalogo.serializar()
    return render_template(
        "seleccionJuego.html",
        juegos=juegos,
        juegos_json=juegos_json,
        juego_inicial=juego_id
    )


# ── API ───────────────────────────────────────────────────────────────────────

@app.route("/api/juegos")
def api_juegos():
    """Devuelve el catálogo completo como JSON."""
    return jsonify({"juegos": catalogo.serializar()})


@app.route("/api/juegos/<juego_id>")
def api_juego_detalle(juego_id: str):
    """Devuelve los datos de un juego concreto."""
    juego = catalogo.obtener_por_id(juego_id)
    if juego is None:
        return jsonify({"error": f"Juego '{juego_id}' no encontrado"}), 404
    return jsonify({
        "id":          juego.id,
        "titulo":      juego.titulo,
        "descripcion": juego.descripcion,
        "genero":      juego.genero,
        "icono":       juego.icono,
        "color":       juego.color,
        "url":         juego.url,
    })


@app.route("/api/score", methods=["POST"])
def api_score():
    """Simula el guardado de puntuación."""
    data = request.get_json()
    print(f"  [API] Puntuación recibida: {data}")
    return jsonify({"status": "ok", "message": "Score saved"})



# ── Punto de entrada ───────────────────────────────────────────────────────────
if __name__ == "__main__":
    def puerto_disponible(port: int) -> bool:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                sock.bind(("127.0.0.1", port))
                return True
            except OSError:
                return False

    puerto_preferido = int(os.getenv("ARCADE_HUB_PORT", "8080"))
    puertos_candidatos = []
    for port in [puerto_preferido, 8085, 8090, 5000]:
        if port not in puertos_candidatos:
            puertos_candidatos.append(port)

    puerto_hub = next((p for p in puertos_candidatos if puerto_disponible(p)), None)
    if puerto_hub is None:
        raise RuntimeError("No hay puertos libres para iniciar el frontend (probados: 8080, 8085, 8090, 5000)")

    sep = "=" * 55
    print(sep)
    print("  [*] ARCADE HUB - Servidor de menu")
    print(sep)
    print(f"  URL:  http://localhost:{puerto_hub}")
    print(f"  Sala de pruebas: http://localhost:{puerto_hub}/pruebas/sala-mentira")
    print(f"  Juegos disponibles: {len(catalogo.obtener_todos())}")
    for j in catalogo.obtener_todos():
        print(f"    - {j.titulo}  ->  {j.url}")
    if puerto_hub != puerto_preferido:
        print(f"  Aviso: puerto {puerto_preferido} ocupado. Usando puerto alternativo {puerto_hub}.")
    print(sep)
    print("  Hub unificado listo.")
    print("  Presiona Ctrl+C para detener el servidor.")
    print(sep)

    app.run(debug=True, host="127.0.0.1", port=puerto_hub)
