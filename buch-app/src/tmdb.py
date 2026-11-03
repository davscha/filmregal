"""Holt Poster, Hintergrundbild und Beschreibung von TheMovieDB (TMDB)."""
import threading

import requests

from speicher import laden, speichern

BILDER = "https://image.tmdb.org/t/p/"


class Tmdb:
    def __init__(self, schluessel: str):
        self.schluessel = schluessel.strip()
        self.gemerkt = laden("tmdb.json", {})       # schon gefundene Infos, auch nach dem Neustart
        self.sperre = threading.Lock()

    def _suchen(self, art: str, **werte) -> list[dict]:
        """Eine Suche bei TMDB. art ist "movie" für Filme oder "tv" für Serien."""
        werte["language"] = "de-DE"
        kopf = {}
        if len(self.schluessel) > 40:                  # langer „Lesezugriffs-Token“
            kopf["Authorization"] = f"Bearer {self.schluessel}"
        else:                                          # kurzer „API-Schlüssel“
            werte["api_key"] = self.schluessel
        antwort = requests.get(f"https://api.themoviedb.org/3/search/{art}",
                               params=werte, headers=kopf, timeout=10)
        antwort.raise_for_status()
        return antwort.json()["results"]

    def _finden(self, art: str, titel: str, jahr: str) -> dict | None:
        jahr_feld = "primary_release_year" if art == "movie" else "first_air_date_year"
        if jahr:
            treffer = self._suchen(art, query=titel, **{jahr_feld: jahr})
            if treffer:
                return treffer[0]
        for t in self._suchen(art, query=titel):
            t_jahr = (t.get("release_date") or t.get("first_air_date") or "")[:4]
            # Ohne Jahr nehmen wir den ersten Treffer, mit Jahr darf es höchstens 1 Jahr abweichen
            if not jahr or (t_jahr.isdigit() and abs(int(t_jahr) - int(jahr)) <= 1):
                return t
        return None

    def info(self, art: str, titel: str, jahr: str) -> dict | None:
        """Liefert ein Dictionary mit titel, jahr, poster, hintergrund, text, bewertung.
        {} heißt: nichts gefunden. None heißt: gerade keine Antwort (kein Schlüssel, kein Internet)."""
        merkname = f"{art}|{titel.lower()}|{jahr}"
        with self.sperre:
            if merkname in self.gemerkt:
                return self.gemerkt[merkname]
        if not self.schluessel:
            return None
        try:
            t = self._finden(art, titel, jahr)
        except requests.RequestException:
            return None                                # später noch einmal versuchen
        info = {}
        if t:
            info = {
                "titel": t.get("title") or t.get("name") or titel,
                "jahr": (t.get("release_date") or t.get("first_air_date") or "")[:4],
                "poster": BILDER + "w342" + t["poster_path"] if t.get("poster_path") else "",
                "hintergrund": BILDER + "w1280" + t["backdrop_path"] if t.get("backdrop_path") else "",
                "text": t.get("overview") or "",
                "bewertung": round(t.get("vote_average") or 0, 1),
            }
        with self.sperre:
            self.gemerkt[merkname] = info
            speichern("tmdb.json", self.gemerkt)
        return info
