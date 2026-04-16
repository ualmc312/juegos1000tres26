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
from flask import Flask, render_template, jsonify, send_from_directory, request

# Ajustamos las rutas de templates y static al directorio de la vista
BASE_DIR      = os.path.dirname(os.path.abspath(__file__))
TEMPLATE_DIR  = os.path.join(BASE_DIR, "src", "menu", "vistaSeleccionJuego")
STATIC_DIR    = os.path.join(BASE_DIR, "src", "menu", "vistaSeleccionJuego")

# Importamos el catálogo de juegos
import sys
sys.path.insert(0, TEMPLATE_DIR)
from listaJuegos import ListaJuegos  # noqa: E402

# ── Configuración Flask ───────────────────────────────────────────────────────
app = Flask(
    __name__,
    template_folder=TEMPLATE_DIR,
    static_folder=STATIC_DIR,
    static_url_path="/static_menu",
)

catalogo = ListaJuegos()

# ── Blueprints de Juegos ──────────────────────────────────────────────────────
from flask import Blueprint

# Space Invaders Blueprint - Servidor interno para el iframe
space_invaders_bp = Blueprint(
    'space_invaders', 
    __name__,
    template_folder=os.path.join(BASE_DIR, "src", "juegos", "SpaceInvaders", "templates"),
    static_folder=os.path.join(BASE_DIR, "src", "juegos", "SpaceInvaders", "static"),
    static_url_path="/juegos/SpaceInvaders/static"
)

@space_invaders_bp.route("/")
def space_invaders_home():
    return render_template("space_invaders.html")

# Registramos el servidor interno del juego
app.register_blueprint(space_invaders_bp, url_prefix="/server/space_invaders")


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
    sep = "=" * 55
    print(sep)
    print("  [*] ARCADE HUB - Servidor de menu")
    print(sep)
    print("  URL:  http://localhost:8080")
    print(f"  Juegos disponibles: {len(catalogo.obtener_todos())}")
    for j in catalogo.obtener_todos():
        print(f"    - {j.titulo}  ->  {j.url}")
    print(sep)
    print("  Hub unificado listo.")
    print("  Presiona Ctrl+C para detener el servidor.")
    print(sep)

    app.run(debug=True, host="127.0.0.1", port=8080)
