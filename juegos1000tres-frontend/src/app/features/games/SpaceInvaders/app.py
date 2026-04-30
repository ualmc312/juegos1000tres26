import json
import os
from urllib import error as urllib_error
from urllib import parse as urllib_parse
from urllib import request as urllib_request

from flask import Flask, render_template, request, jsonify

from datetime import datetime

app = Flask(__name__)

# Command names used by the event endpoint
COMANDO_ACTUALIZAR_PUNTUACION = "ACTUALIZAR_PUNTUACION"
COMANDO_NOTIFICAR_MUERTE = "NOTIFICAR_MUERTE"

BACKEND_BASE_URL = os.getenv("SPACE_INVADERS_BACKEND_URL", "http://127.0.0.1:8082/api/pruebas/space-invaders").rstrip("/")

_jugadores_estado = {}


def _extraer_player_id_desde_query():
    player_id = request.args.get('playerId', type=str)
    if not isinstance(player_id, str) or not player_id.strip():
        return None

    return player_id.strip()


def _invocar_backend(method, path, payload=None, query=None):
    url = f"{BACKEND_BASE_URL}{path}"
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
            "backendBaseUrl": BACKEND_BASE_URL,
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


def _actualizar_estado_local(data):
    if not isinstance(data, dict):
        return

    comando = data.get("comando")
    jugador_id = data.get("jugadorId")
    nombre = data.get("nombreJugador")
    puntuacion = data.get("puntuacion")

    if not isinstance(jugador_id, str) or not jugador_id.strip():
        return

    jugador_id = jugador_id.strip()
    estado = _jugadores_estado.get(jugador_id, {
        "jugadorId": jugador_id,
        "nombreJugador": nombre.strip() if isinstance(nombre, str) and nombre.strip() else "Jugador",
        "puntuacion": 0,
        "muerto": False,
    })

    if isinstance(nombre, str) and nombre.strip():
        estado["nombreJugador"] = nombre.strip()

    if isinstance(puntuacion, (int, float)):
        estado["puntuacion"] = int(puntuacion)

    if comando == COMANDO_NOTIFICAR_MUERTE:
        estado["muerto"] = True
    elif comando == COMANDO_ACTUALIZAR_PUNTUACION:
        estado["muerto"] = False

    estado["actualizadoEn"] = datetime.utcnow().isoformat() + "Z"
    _jugadores_estado[jugador_id] = estado


def _estado_local_payload():
    jugadores = list(_jugadores_estado.values())
    jugadores.sort(key=lambda item: item.get("puntuacion", 0), reverse=True)
    return {
        "comando": "ESTADO_JUGADORES",
        "jugadores": jugadores,
    }

@app.route('/')
def home():
    return render_template('space_invaders.html')

@app.route('/pantalla')
def pantalla():
    screen_id = request.args.get('screenId', type=str)
    if not isinstance(screen_id, str) or not screen_id.strip():
        screen_id = 'pantalla-principal'

    return render_template('space_invaders_scoreboard.html', screen_id=screen_id.strip())

@app.route('/api/score', methods=['GET'])
def get_score():
    status_code, body_bytes, content_type = _invocar_backend("GET", "/score")
    if status_code is None or status_code >= 400:
        jugadores = list(_jugadores_estado.values())
        jugadores.sort(key=lambda item: item.get("puntuacion", 0), reverse=True)
        scores = [
            {
                "jugadorId": item.get("jugadorId"),
                "player": item.get("nombreJugador"),
                "score": item.get("puntuacion", 0),
                "dead": bool(item.get("muerto", False)),
            }
            for item in jugadores
        ]
        return jsonify({"scores": scores}), 200

    return _respuesta_proxy(status_code, body_bytes, content_type)


@app.route('/api/updates', methods=['GET'])
def get_updates():
    player_id = _extraer_player_id_desde_query()
    screen_id = request.args.get('screenId', type=str)
    if player_id is None and not (isinstance(screen_id, str) and screen_id.strip()):
        return jsonify({"status": "error", "message": "Missing query param 'playerId'"}), 400

    status_code, body_bytes, content_type = _invocar_backend("GET", "/updates", query={"playerId": player_id})
    if status_code is None or status_code >= 400:
        return jsonify(_estado_local_payload()), 200

    return _respuesta_proxy(status_code, body_bytes, content_type)


@app.route('/api/event', methods=['POST'])
def procesar_evento():
    data = request.get_json()
    if not isinstance(data, dict):
        return jsonify({"status": "error", "message": "Invalid JSON"}), 400

    _actualizar_estado_local(data)

    status_code, body_bytes, content_type = _invocar_backend("POST", "/event", payload=data)
    if status_code is None or status_code >= 400:
        return jsonify({"status": "ok"}), 200

    return _respuesta_proxy(status_code, body_bytes, content_type)


@app.route('/api/score', methods=['POST'])
def save_score():
    # Backward-compatible endpoint. Transforms legacy payload into game event.
    data = request.get_json()
    if data and 'score' in data and 'player' in data:
        player_name = str(data['player']).strip()

        if player_name:
            try:
                score = int(data['score'])
            except (TypeError, ValueError):
                return jsonify({"status": "error", "message": "Score must be numeric"}), 400

            event_payload = {
                "comando": COMANDO_ACTUALIZAR_PUNTUACION,
                "jugadorId": f"name:{player_name.lower()}",
                "nombreJugador": player_name,
                "puntuacion": score,
            }

            status_code, body_bytes, content_type = _invocar_backend("POST", "/event", payload=event_payload)
            return _respuesta_proxy(status_code, body_bytes, content_type)

    return jsonify({"status": "error", "message": "Invalid data, Requires 'player' and 'score'"}), 400

if __name__ == '__main__':
    # Listen on all standard network interfaces so other LAN users can connect
    app.run(debug=True, host='0.0.0.0', port=5000)
