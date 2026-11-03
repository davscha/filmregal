"""Zwei Quellen für Videos mit denselben Methoden: ein Ordner am PC und das NAS."""
import io
import threading
from dataclasses import dataclass
from pathlib import Path

from smb.base import NotConnectedError
from smb.SMBConnection import SMBConnection

VERSTECKT = {"system volume information", "lost+found", "recycler", "$recycle.bin", "fritz"}


@dataclass
class Eintrag:
    name: str          # "Vaiana [2026].mkv"
    pfad: str          # "Kino/Vaiana [2026].mkv" – immer mit / getrennt
    ist_ordner: bool


def unsichtbar(name: str) -> bool:
    return name.startswith((".", "@", "$")) or name.lower() in VERSTECKT


class OrdnerQuelle:
    """Videos aus einem normalen Ordner, z. B. dem Testregal am PC."""

    def __init__(self, wurzel: str):
        self.wurzel = Path(wurzel)
        self.kennung = f"ordner:{self.wurzel}"

    def liste(self, pfad: str = "") -> list[Eintrag]:
        eintraege = []
        for p in (self.wurzel / pfad).iterdir():
            if not unsichtbar(p.name):
                eintraege.append(Eintrag(p.name, f"{pfad}/{p.name}".strip("/"), p.is_dir()))
        return sorted(eintraege, key=lambda e: e.name.lower())

    def groesse(self, pfad: str) -> int:
        return (self.wurzel / pfad).stat().st_size

    def lesen(self, pfad: str, ab: int, anzahl: int) -> bytes:
        with open(self.wurzel / pfad, "rb") as datei:
            datei.seek(ab)
            return datei.read(anzahl)


class NasQuelle:
    """Videos vom NAS über SMB – dasselbe Verfahren wie „Netzlaufwerk verbinden“ unter Windows."""

    def __init__(self, adresse: str, benutzer: str, passwort: str, ordner: str = ""):
        self.adresse = adresse.strip()
        self.benutzer = benutzer.strip()
        self.passwort = passwort
        self.ordner = ordner.strip().strip("/")         # z. B. "FRITZ.NAS/USB-Platte/Filme"
        self.kennung = f"nas:{self.adresse}/{self.ordner}"
        self.verbindung = None
        self.sperre = threading.Lock()                  # immer nur ein Zugriff gleichzeitig

    def _verbinden(self) -> SMBConnection:
        letzter_fehler = None
        # Erst das moderne Anmeldeverfahren NTLMv2, dann das alte NTLMv1 (ältere FritzBoxen)
        for ntlm_v2 in (True, False):
            verbindung = SMBConnection(self.benutzer or "guest", self.passwort, "filmregal", "",
                                       use_ntlm_v2=ntlm_v2, is_direct_tcp=True)
            try:
                if verbindung.connect(self.adresse, 445, timeout=10):
                    return verbindung
            except Exception as fehler:
                letzter_fehler = fehler
        raise ConnectionError(f"Anmeldung am NAS fehlgeschlagen: {letzter_fehler or 'Benutzer oder Passwort falsch'}")

    def _ausfuehren(self, aufgabe):
        """Führt aufgabe(verbindung) aus. Ist die Verbindung abgerissen, wird einmal neu verbunden."""
        with self.sperre:
            for versuch in (1, 2):
                if self.verbindung is None:
                    self.verbindung = self._verbinden()
                try:
                    return aufgabe(self.verbindung)
                except (NotConnectedError, OSError):
                    self.verbindung = None
                    if versuch == 2:
                        raise

    def _aufteilen(self, pfad: str) -> tuple[str, str]:
        """'Kino/Vaiana.mkv' → ('FRITZ.NAS', '/USB-Platte/Filme/Kino/Vaiana.mkv')"""
        voll = f"{self.ordner}/{pfad}".strip("/")
        freigabe, _, rest = voll.partition("/")
        return freigabe, "/" + rest

    def liste(self, pfad: str = "") -> list[Eintrag]:
        freigabe, innen = self._aufteilen(pfad)

        def auflisten(verbindung):
            if not freigabe:                            # ganz oben: die Freigaben selbst
                return [Eintrag(f.name, f.name, True) for f in verbindung.listShares(timeout=15)
                        if not f.isSpecial and not f.name.endswith("$")]
            eintraege = []
            for f in verbindung.listPath(freigabe, innen, timeout=30):
                if f.filename not in (".", "..") and not unsichtbar(f.filename):
                    eintraege.append(Eintrag(f.filename, f"{pfad}/{f.filename}".strip("/"), f.isDirectory))
            return eintraege

        return sorted(self._ausfuehren(auflisten), key=lambda e: e.name.lower())

    def groesse(self, pfad: str) -> int:
        freigabe, innen = self._aufteilen(pfad)
        return self._ausfuehren(lambda v: v.getAttributes(freigabe, innen, timeout=15).file_size)

    def lesen(self, pfad: str, ab: int, anzahl: int) -> bytes:
        freigabe, innen = self._aufteilen(pfad)

        def stueck_holen(verbindung):
            puffer = io.BytesIO()
            verbindung.retrieveFileFromOffset(freigabe, innen, puffer, ab, anzahl, timeout=30)
            return puffer.getvalue()

        return self._ausfuehren(stueck_holen)


def quelle_erstellen(einstellungen: dict):
    if einstellungen["quelle"] == "nas":
        return NasQuelle(einstellungen["nas_adresse"], einstellungen["nas_benutzer"],
                         einstellungen["nas_passwort"], einstellungen["nas_ordner"])
    return OrdnerQuelle(einstellungen["ordner"])
