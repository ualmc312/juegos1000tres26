import json
import os
from urllib import error as urllib_error
from urllib import parse as urllib_parse
from urllib import request as urllib_request

from flask import Blueprint, jsonify, render_template, request

COMANDO_ACTUALIZAR_PUNTUACION = "ACTUALIZAR_PUNTUACION"


def create_space_invaders_blueprint(base_dir: str) -> Blueprint:
    if not isinstance(base_dir, str) or not base_dir.strip():
        raise ValueError("base_dir es obligatorio para crear el blueprint de Space Invaders")

    legacy_backend_base_url = os.getenv("SPACE_INVADERS_BACKEND_URL", "").strip().rstrip("/")
    if legacy_backend_base_url:
        backend_event_url = f"{legacy_backend_base_url}/event"
        backend_updates_url = f"{legacy_backend_base_url}/updates"
    else:
        backend_event_url = os.getenv(
            "SPACE_INVADERS_BACKEND_EVENT_URL",
            "http://127.0.0.1:8081/api/salas/space-invaders/eventos",
        ).strip()
        backend_updates_url = os.getenv(
            "SPACE_INVADERS_BACKEND_UPDATES_URL",
            "http://127.0.0.1:8081/api/salas/space-invaders/actualizaciones",
        ).strip()

    blueprint = Blueprint(
        "space_invaders",
        __name__,
        template_folder=os.path.join(base_dir, "src", "juegos", "SpaceInvaders", "templates"),
        static_folder=os.path.join(base_dir, "src", "juegos", "SpaceInvaders", "static"),
        static_url_path="/juegos/SpaceInvaders/static",
    )

    @blueprint.route("/")
    def space_invaders_home():
        return render_template("space_invaders.html")

    @blueprint.route("/pantalla")
    def space_invaders_pantalla():
        screen_id = request.args.get("screenId", type=str)
        if not isinstance(screen_id, str) or not screen_id.strip():
            screen_id = "pantalla-principal"

        return render_template("space_invaders_scoreboard.html", screen_id=screen_id.strip())

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
                "message": "No se pudo conectar con el backend Java de Space Invaders",
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

    @blueprint.route("/api/score", methods=["GET"])
    def space_invaders_get_score():
        status_code, body_bytes, content_type = _invocar_backend("GET", backend_updates_url)

        if status_code is None:
            return _respuesta_proxy(status_code, body_bytes, content_type)

        if status_code == 204 or not body_bytes:
            return jsonify({"scores": []}), 200

        try:
            payload = json.loads(body_bytes.decode("utf-8"))
            jugadores = payload.get("jugadores", []) if isinstance(payload, dict) else []
            if isinstance(jugadores, list):
                scores = []
                for jugador in jugadores:
                    if not isinstance(jugador, dict):
                        continue
                    scores.append({
                        "jugadorId": jugador.get("jugadorId") or jugador.get("playerId"),
                        "player": jugador.get("nombreJugador") or jugador.get("player") or "Jugador",
                        "score": jugador.get("puntuacion") if isinstance(jugador.get("puntuacion"), int)
                        else jugador.get("score", 0),
                        "dead": bool(jugador.get("muerto") if "muerto" in jugador else jugador.get("dead", False)),
                    })

                return jsonify({"scores": scores}), 200
        except Exception:
            pass

        return _respuesta_proxy(status_code, body_bytes, content_type)

    @blueprint.route("/api/updates", methods=["GET"])
    def space_invaders_get_updates():
        query = {
            key: value
            for key, value in request.args.items()
            if isinstance(value, str) and value.strip()
        }
        if not query:
            query = None

        status_code, body_bytes, content_type = _invocar_backend(
            "GET",
            backend_updates_url,
            query=query,
        )
        return _respuesta_proxy(status_code, body_bytes, content_type)

    @blueprint.route("/api/event", methods=["POST"])
    def space_invaders_procesar_evento():
        data = request.get_json()
        if not isinstance(data, dict):
            return jsonify({"status": "error", "message": "Invalid JSON"}), 400

        status_code, body_bytes, content_type = _invocar_backend("POST", backend_event_url, payload=data)
        return _respuesta_proxy(status_code, body_bytes, content_type)

    @blueprint.route("/api/score", methods=["POST"])
    def space_invaders_save_score():
        data = request.get_json()
        if data and "score" in data and "player" in data and "playerId" in data:
            player_name = str(data["player"]).strip()
            player_id = str(data["playerId"]).strip()

            if player_name and player_id:
                try:
                    score = int(data["score"])
                except (TypeError, ValueError):
                    return jsonify({"status": "error", "message": "Score must be numeric"}), 400

                event_payload = {
                    "comando": COMANDO_ACTUALIZAR_PUNTUACION,
                    "jugadorId": player_id,
                    "nombreJugador": player_name,
                    "puntuacion": score,
                }

                status_code, body_bytes, content_type = _invocar_backend(
                    "POST",
                    backend_event_url,
                    payload=event_payload,
                )
                return _respuesta_proxy(status_code, body_bytes, content_type)

        return jsonify({"status": "error", "message": "Invalid data, Requires 'playerId', 'player' and 'score'"}), 400

    return blueprint
