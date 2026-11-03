"""Ein kleiner Webserver auf dem eigenen Gerät, der dem Player die Videos liefert.

Der Player versteht nur Adressen wie http://…, aber kein SMB. Der Server ist der Dolmetscher:
Der Player fragt ihn per HTTP nach einem Stück des Videos, der Server liest genau dieses
Stück aus der Quelle (Ordner oder NAS) und schickt es zurück.
"""
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import quote, unquote

STUECK = 1024 * 1024                                   # 1 MB pro Lesevorgang
ARTEN = {"mkv": "video/x-matroska", "mp4": "video/mp4", "m4v": "video/mp4", "webm": "video/webm",
         "avi": "video/x-msvideo", "mov": "video/quicktime", "ts": "video/mp2t"}


class Anfrage(BaseHTTPRequestHandler):
    """Beantwortet eine Anfrage des Players. Für jede Anfrage läuft ein eigener Thread."""

    def do_HEAD(self):
        self.antworten(mit_daten=False)

    def do_GET(self):
        self.antworten(mit_daten=True)

    def antworten(self, mit_daten: bool):
        quelle = self.server.quelle
        pfad = unquote(self.path.lstrip("/"))
        try:
            groesse = quelle.groesse(pfad)
        except Exception:
            self.send_error(404, "Datei nicht gefunden")
            return

        # Der Player sagt im "Range"-Kopf, welches Stück er möchte, z. B. "bytes=5000000-"
        start, ende = 0, groesse - 1
        bereich = self.headers.get("Range", "")
        if bereich.startswith("bytes="):
            von, _, bis = bereich[6:].split(",")[0].partition("-")
            if von:
                start = int(von)
                ende = min(int(bis), groesse - 1) if bis else groesse - 1
            else:                                      # "bytes=-500" heißt: die letzten 500 Bytes
                start = max(0, groesse - int(bis))
            if start >= groesse:
                self.send_response(416)                # Stück liegt hinter dem Dateiende
                self.send_header("Content-Range", f"bytes */{groesse}")
                self.end_headers()
                return
            self.send_response(206)                    # 206 = „hier ist das gewünschte Stück“
            self.send_header("Content-Range", f"bytes {start}-{ende}/{groesse}")
        else:
            self.send_response(200)                    # 200 = „hier ist die ganze Datei“

        endung = pfad.rsplit(".", 1)[-1].lower()
        self.send_header("Content-Type", ARTEN.get(endung, "application/octet-stream"))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(ende - start + 1))
        self.end_headers()
        if not mit_daten:
            return

        position = start
        try:
            while position <= ende:
                daten = quelle.lesen(pfad, position, min(STUECK, ende - position + 1))
                if not daten:
                    break
                self.wfile.write(daten)
                position += len(daten)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass                                       # Der Player hat gespult oder ist fertig – ganz normal

    def log_message(self, *argumente):
        pass                                           # keine Ausgabe für jede einzelne Anfrage


class StreamServer:
    def __init__(self):
        # Port 0 heißt: Das System sucht einen freien Port aus. 127.0.0.1 heißt: nur dieses Gerät.
        self.http = ThreadingHTTPServer(("127.0.0.1", 0), Anfrage)
        self.http.daemon_threads = True
        self.http.quelle = None
        threading.Thread(target=self.http.serve_forever, daemon=True).start()

    def adresse(self, quelle, pfad: str) -> str:
        """Die Adresse, unter der der Player das Video abholen kann."""
        self.http.quelle = quelle
        port = self.http.server_address[1]
        return f"http://127.0.0.1:{port}/{quote(pfad)}"
