import json
import os
from urllib import error as urllib_error
from urllib import request as urllib_request

from flask import Blueprint, jsonify, render_template


def create_prueba_websocket_blueprint(base_dir: str) -> Blueprint:
    if not isinstance(base_dir, str) or not base_dir.strip():
        raise ValueError("base_dir es obligatorio para crear el blueprint de PruebaWebSocket")

    backend_base_url = os.getenv(
        "PRUEBA_WEBSOCKET_BACKEND_URL",
        "http://127.0.0.1:8082/api/pruebas/websocket-chat",
    ).rstrip("/")

    blueprint = Blueprint(
        "prueba_websocket",
        __name__,
        template_folder=os.path.join(base_dir, "src", "juegos", "PruebaWebSocket", "templates"),
        static_folder=os.path.join(base_dir, "src", "juegos", "PruebaWebSocket", "static"),
        static_url_path="/juegos/PruebaWebSocket/static",
    )

    @blueprint.route("/")
    def prueba_websocket_home():
        return render_template("prueba_websocket.html")

    @blueprint.route("/pantalla")
    def prueba_websocket_pantalla():
        return render_template("prueba_websocket_pantalla.html")

    def _invocar_backend(path: str):
        req = urllib_request.Request(
            url=f"{backend_base_url}{path}",
            headers={"Accept": "application/json"},
            method="GET",
        )

        try:
            with urllib_request.urlopen(req, timeout=4) as response:
                return response.getcode(), response.read()
        except urllib_error.HTTPError as error:
            return error.code, error.read()
        except Exception:
            return None, None

    @blueprint.route("/api/config")
    def prueba_websocket_config():
        status_code, body_bytes = _invocar_backend("/config")
        if status_code is None:
            return jsonify({
                "status": "error",
                "message": "No se pudo obtener configuracion de backend para PruebaWebSocket",
                "backendBaseUrl": backend_base_url,
            }), 502

        if not body_bytes:
            return ("", status_code)

        try:
            payload = json.loads(body_bytes.decode("utf-8"))
            return jsonify(payload), status_code
        except Exception:
            return body_bytes, status_code

    return blueprint
