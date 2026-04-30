import os

from flask import Blueprint, render_template


def create_prueba_websocket_blueprint(base_dir: str) -> Blueprint:
    if not isinstance(base_dir, str) or not base_dir.strip():
        raise ValueError("base_dir es obligatorio para crear el blueprint de PruebaWebSocket")

    ws_jugadores_url = os.getenv(
        "PRUEBA_WEBSOCKET_WS_JUGADORES_URL",
        "ws://127.0.0.1:8091/ws/salas/prueba-websocket-jugadores",
    ).strip()
    ws_pantalla_url = os.getenv(
        "PRUEBA_WEBSOCKET_WS_PANTALLA_URL",
        "ws://127.0.0.1:8091/ws/salas/prueba-websocket-pantalla",
    ).strip()

    blueprint = Blueprint(
        "prueba_websocket",
        __name__,
        template_folder=os.path.join(base_dir, "src", "juegos", "PruebaWebSocket", "templates"),
        static_folder=os.path.join(base_dir, "src", "juegos", "PruebaWebSocket", "static"),
        static_url_path="/juegos/PruebaWebSocket/static",
    )

    @blueprint.route("/")
    def prueba_websocket_home():
        return render_template(
            "prueba_websocket.html",
            ws_jugadores_url=ws_jugadores_url,
        )

    @blueprint.route("/pantalla")
    def prueba_websocket_pantalla():
        return render_template(
            "prueba_websocket_pantalla.html",
            ws_pantalla_url=ws_pantalla_url,
        )

    return blueprint
