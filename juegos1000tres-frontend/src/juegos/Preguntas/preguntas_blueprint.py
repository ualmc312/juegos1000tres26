import json
import os
from urllib import error as urllib_error
from urllib import parse as urllib_parse
from urllib import request as urllib_request

from flask import Blueprint, jsonify, render_template, request


def create_preguntas_blueprint(base_dir: str) -> Blueprint:
    if not isinstance(base_dir, str) or not base_dir.strip():
        raise ValueError("base_dir es obligatorio para crear el blueprint de Preguntas")

    backend_event_url = os.getenv(
        "PREGUNTAS_BACKEND_EVENT_URL",
        "http://127.0.0.1:8081/api/salas/preguntas/eventos",
    ).strip()
    backend_updates_url = os.getenv(
        "PREGUNTAS_BACKEND_UPDATES_URL",
        "http://127.0.0.1:8081/api/salas/preguntas/actualizaciones",
    ).strip()

    blueprint = Blueprint(
        "preguntas",
        __name__,
        template_folder=os.path.join(base_dir, "src", "juegos", "Preguntas", "templates"),
        static_folder=os.path.join(base_dir, "src", "juegos", "Preguntas", "static"),
        static_url_path="/juegos/Preguntas/static",
    )

    @blueprint.route("/")
    def preguntas_home():
        return render_template("preguntas.html")

    @blueprint.route("/pantalla")
    def preguntas_pantalla():
        screen_id = request.args.get("screenId", type=str)
        if not isinstance(screen_id, str) or not screen_id.strip():
            screen_id = "pantalla-principal"

        return render_template("preguntas_pantalla.html", screen_id=screen_id.strip())

    def _invocar_backend(method, url, payload=None, query=None):
        if query:
            url = f"{url}?{urllib_parse.urlencode(query)}"

        headers = {
            "Accept": "application/json",
        }
        data_bytes = None

        if payload is not None:
            headers["Content-Type"] = "application/json"
            data_bytes = json.dumps(payload).encode("utf-8")

        req = urllib_request.Request(url=url, data=data_bytes, headers=headers, method=method)

        try:
            with urllib_request.urlopen(req, timeout=4) as response:
                return response.getcode(), response.read(), response.headers.get("Content-Type", "application/json")
        except urllib_error.HTTPError as error:
            return error.code, error.read(), error.headers.get("Content-Type", "application/json")
        except Exception:
            return None, None, None

    def _respuesta_proxy(status_code, body_bytes, _content_type):
        if status_code is None:
            return jsonify({
                "status": "error",
                "message": "No se pudo conectar con el backend Java de Preguntas",
                "backendEventUrl": backend_event_url,
                "backendUpdatesUrl": backend_updates_url,
            }), 502

        if status_code == 204:
            return ("", 204)

        if not body_bytes:
            return ("", status_code)

        try:
            body_json = json.loads(body_bytes.decode("utf-8"))
            return jsonify(body_json), status_code
        except Exception:
            return body_bytes, status_code

    @blueprint.route("/api/estado", methods=["GET"])
    def preguntas_get_estado():
        status_code, body_bytes, content_type = _invocar_backend("GET", backend_updates_url)
        return _respuesta_proxy(status_code, body_bytes, content_type)

    @blueprint.route("/api/updates", methods=["GET"])
    def preguntas_get_updates():
        status_code, body_bytes, content_type = _invocar_backend("GET", backend_updates_url)
        return _respuesta_proxy(status_code, body_bytes, content_type)

    @blueprint.route("/api/event", methods=["POST"])
    def preguntas_procesar_evento():
        data = request.get_json()
        if not isinstance(data, dict):
            return jsonify({"status": "error", "message": "Invalid JSON"}), 400

        status_code, body_bytes, content_type = _invocar_backend("POST", backend_event_url, payload=data)
        return _respuesta_proxy(status_code, body_bytes, content_type)

    return blueprint
