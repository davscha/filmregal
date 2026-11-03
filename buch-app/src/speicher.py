"""Speichert Einstellungen und andere Daten als JSON-Dateien."""
import json
import os
from pathlib import Path

# Flet nennt uns einen sicheren Datenordner – unter Android ist das der einzige,
# in den die App schreiben darf. Ohne Flet (z. B. im Test) nehmen wir einen Ordner im Benutzerprofil.
DATEN = Path(os.getenv("FLET_APP_STORAGE_DATA") or Path.home() / "Filmregal-Daten")
DATEN.mkdir(parents=True, exist_ok=True)

STANDARD = {
    "quelle": "ordner",                               # "ordner" oder "nas"
    "ordner": str(Path.home() / "Testregal"),
    "nas_adresse": "192.168.178.1",
    "nas_benutzer": "",
    "nas_passwort": "",
    "nas_ordner": "",
    "tmdb_schluessel": "",
}


def laden(name: str, standard):
    """Liest eine JSON-Datei. Fehlt sie oder ist sie kaputt, gibt es den Standardwert."""
    try:
        return json.loads((DATEN / name).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return standard


def speichern(name: str, daten) -> None:
    (DATEN / name).write_text(json.dumps(daten, ensure_ascii=False, indent=1), encoding="utf-8")


def einstellungen_laden() -> dict:
    return STANDARD | laden("einstellungen.json", {})
