import os
from flask import Blueprint, render_template, request, redirect, url_for, session


class SalaMentiraPruebas:
    """Modulo aislado para pruebas manuales de comunicacion en una unica sala."""

    URL_PREFIX = "/pruebas/sala-mentira"
    SESSION_KEY_NOMBRE = "sala_mentira_nombre_usuario"
    SESSION_KEY_TIPO_CLIENTE = "sala_mentira_tipo_cliente"
    SESSION_KEY_JUEGO = "sala_mentira_juego_id"

    def __init__(self, base_dir: str) -> None:
        self.base_dir = base_dir

    def crear_blueprint(self) -> Blueprint:
        blueprint = Blueprint(
            "sala_mentira_pruebas",
            __name__,
            template_folder=os.path.join(self.base_dir, "src", "pruebas", "templates"),
            static_folder=os.path.join(self.base_dir, "src", "pruebas", "static"),
            static_url_path="/pruebas/sala-mentira/static",
        )

        @blueprint.get("/")
        def pedir_nombre():
            nombre_guardado = self._normalizar_nombre(session.get(self.SESSION_KEY_NOMBRE, ""))
            if nombre_guardado:
                return redirect(url_for("sala_mentira_pruebas.ver_sala"))

            return render_template(
                "sala_mentira_login.html",
                error=None,
                nombre="",
                tipo_cliente=self._normalizar_tipo_cliente(session.get(self.SESSION_KEY_TIPO_CLIENTE, "jugador")),
                juego_seleccionado=self._normalizar_juego_id(session.get(self.SESSION_KEY_JUEGO, "space_invaders")),
            )

        @blueprint.post("/")
        def guardar_nombre():
            nombre = self._normalizar_nombre(request.form.get("nombre", ""))
            tipo_cliente = self._normalizar_tipo_cliente(request.form.get("tipoCliente", "jugador"))
            juego_id = self._normalizar_juego_id(request.form.get("juegoId", "space_invaders"))
            if not nombre:
                return render_template(
                    "sala_mentira_login.html",
                    error="Escribe un nombre valido (1 a 24 caracteres).",
                    nombre="",
                    tipo_cliente=tipo_cliente,
                    juego_seleccionado=juego_id,
                )

            session[self.SESSION_KEY_NOMBRE] = nombre
            session[self.SESSION_KEY_TIPO_CLIENTE] = tipo_cliente
            session[self.SESSION_KEY_JUEGO] = juego_id
            return redirect(url_for("sala_mentira_pruebas.ver_sala"))

        @blueprint.get("/sala")
        def ver_sala():
            nombre_guardado = self._normalizar_nombre(session.get(self.SESSION_KEY_NOMBRE, ""))
            if not nombre_guardado:
                return redirect(url_for("sala_mentira_pruebas.pedir_nombre"))

            tipo_cliente = self._normalizar_tipo_cliente(session.get(self.SESSION_KEY_TIPO_CLIENTE, "jugador"))
            juego_id = self._normalizar_juego_id(session.get(self.SESSION_KEY_JUEGO, "space_invaders"))

            return render_template(
                "sala_mentira_sala.html",
                nombre_usuario=nombre_guardado,
                tipo_cliente=tipo_cliente,
                juego_id=juego_id,
                juego_titulo=self._titulo_juego(juego_id),
                es_pantalla=(tipo_cliente == "pantalla"),
            )

        @blueprint.post("/salir")
        def salir():
            session.pop(self.SESSION_KEY_NOMBRE, None)
            session.pop(self.SESSION_KEY_TIPO_CLIENTE, None)
            session.pop(self.SESSION_KEY_JUEGO, None)
            return redirect(url_for("sala_mentira_pruebas.pedir_nombre"))

        return blueprint

    @staticmethod
    def _normalizar_nombre(valor: str) -> str:
        if not isinstance(valor, str):
            return ""

        limpio = valor.strip()
        if not limpio:
            return ""

        return limpio[:24]

    @staticmethod
    def _normalizar_tipo_cliente(valor: str) -> str:
        if isinstance(valor, str) and valor.strip().lower() == "pantalla":
            return "pantalla"

        return "jugador"

    @staticmethod
    def _normalizar_juego_id(valor: str) -> str:
        if isinstance(valor, str) and valor.strip().lower() == "prueba_websocket":
            return "prueba_websocket"

        if isinstance(valor, str) and valor.strip().lower() == "preguntas":
            return "preguntas"

        return "space_invaders"

    @staticmethod
    def _titulo_juego(juego_id: str) -> str:
        if juego_id == "prueba_websocket":
            return "Prueba WebSocket"

        if juego_id == "preguntas":
            return "Preguntas"

        return "Space Invaders"
