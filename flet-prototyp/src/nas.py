"""NAS-Zugriff per SMB mit pysmb.

pysmb kann SMB1 und SMB2 sowie das alte NTLMv1-Anmeldeverfahren –
genau das, was ältere FritzBoxen brauchen.
"""

from __future__ import annotations

import socket
import threading
from dataclasses import dataclass

from smb.SMBConnection import SMBConnection

HIDDEN = {"system volume information", "fritz", "lost+found", "recycler", "found.000"}


@dataclass
class Entry:
    name: str
    path: str
    is_dir: bool
    added: float = 0


@dataclass
class NasConfig:
    host: str = ""
    share: str = ""
    root: str = ""
    user: str = ""
    password: str = ""


class Nas:
    def __init__(self, cfg: NasConfig):
        self.cfg = cfg
        self._conn: SMBConnection | None = None
        self._lock = threading.Lock()
        self.method = ""

    def _connect(self) -> SMBConnection:
        last_error = None
        # Erst modernes NTLMv2, dann das alte NTLMv1 (ältere FritzBoxen)
        for ntlm_v2 in (True, False):
            try:
                conn = SMBConnection(
                    self.cfg.user or "guest",
                    self.cfg.password,
                    "filmregal",
                    "",
                    use_ntlm_v2=ntlm_v2,
                    is_direct_tcp=True,
                )
                if conn.connect(self.cfg.host, 445, timeout=15):
                    self.method = "NTLMv2" if ntlm_v2 else "NTLMv1"
                    return conn
            except (OSError, socket.timeout) as e:
                last_error = e
            except Exception as e:  # Anmeldung abgelehnt → nächstes Verfahren
                last_error = e
        raise ConnectionError(f"Anmeldung fehlgeschlagen: {last_error or 'Benutzer oder Passwort falsch'}")

    def _split(self, path: str) -> tuple[str, str]:
        """'davscha78/Platte/Filme' → ('davscha78', '/Platte/Filme') wenn keine Freigabe eingetragen ist."""
        if self.cfg.share:
            return self.cfg.share, "/" + path.strip("/")
        parts = path.strip("/").split("/", 1)
        return parts[0], "/" + (parts[1] if len(parts) > 1 else "")

    def list(self, path: str) -> list[Entry]:
        with self._lock:
            for attempt in range(2):
                try:
                    if self._conn is None:
                        self._conn = self._connect()
                    return self._list(path)
                except ConnectionError:
                    raise
                except Exception:
                    # Verbindung abgerissen → einmal neu verbinden
                    self._conn = None
                    if attempt == 1:
                        raise
        return []

    def _list(self, path: str) -> list[Entry]:
        conn = self._conn
        if not self.cfg.share and not path.strip("/"):
            # Oberste Ebene ohne Freigabe: die Freigaben selbst sind die Ordner
            return [
                Entry(s.name, s.name, True)
                for s in conn.listShares(timeout=15)
                if not s.isSpecial and not s.name.endswith("$")
            ]
        share, inner = self._split(path)
        result = []
        for f in conn.listPath(share, inner, timeout=30):
            name = f.filename
            if name in (".", "..") or name.startswith((".", "@", "$")) or name.lower() in HIDDEN:
                continue
            full = f"{path.strip('/')}/{name}" if path.strip("/") else name
            result.append(Entry(name, full, f.isDirectory, f.create_time or f.last_write_time or 0))
        return result

    def close(self):
        with self._lock:
            if self._conn:
                try:
                    self._conn.close()
                except Exception:
                    pass
                self._conn = None
