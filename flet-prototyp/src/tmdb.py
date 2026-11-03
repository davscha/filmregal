"""Poster und Infos von TMDB – Filme mit Jahr, damit nichts Falsches gefunden wird."""

from __future__ import annotations

import json
import os
import threading

import requests

IMG = "https://image.tmdb.org/t/p/w342"


class Tmdb:
    def __init__(self, data_dir: str, api_key: str):
        self.key = api_key.strip()
        self.file = os.path.join(data_dir, "tmdb_cache.json")
        self.lock = threading.Lock()
        try:
            with open(self.file, encoding="utf-8") as f:
                self.cache = json.load(f)
        except Exception:
            self.cache = {}

    def _get(self, path: str, **params) -> dict:
        params["language"] = "de-DE"
        headers = {"Accept": "application/json"}
        if len(self.key) > 40:  # langer v4-Token
            headers["Authorization"] = f"Bearer {self.key}"
        else:
            params["api_key"] = self.key
        r = requests.get(f"https://api.themoviedb.org/3/{path}", params=params, headers=headers, timeout=10)
        r.raise_for_status()
        return r.json()

    def _cached(self, key: str, fetch) -> dict | None:
        with self.lock:
            if key in self.cache:
                return self.cache[key]
        if not self.key:
            return None
        try:
            hit = fetch()
        except Exception:
            return None  # Netzwerkfehler nicht merken, später erneut versuchen
        result = {
            "title": hit.get("title") or hit.get("name"),
            "poster": hit.get("poster_path"),
            "year": (hit.get("release_date") or hit.get("first_air_date") or "")[:4],
            "overview": hit.get("overview"),
        } if hit else {}
        with self.lock:
            self.cache[key] = result
            with open(self.file, "w", encoding="utf-8") as f:
                json.dump(self.cache, f)
        return result

    def movie(self, title: str, year: str | None) -> dict | None:
        def fetch():
            if year:
                res = self._get("search/movie", query=title, primary_release_year=year)["results"]
                if res:
                    return res[0]
            res = self._get("search/movie", query=title)["results"]
            if not year:
                return res[0] if res else None
            # Ohne exakten Treffer nur höchstens 1 Jahr Abweichung akzeptieren
            for r in res:
                y = (r.get("release_date") or "")[:4]
                if y.isdigit() and abs(int(y) - int(year)) <= 1:
                    return r
            return None

        return self._cached(f"m|{title.lower()}|{year or ''}", fetch)

    def show(self, name: str, year: str | None) -> dict | None:
        def fetch():
            if year:
                res = self._get("search/tv", query=name, first_air_date_year=year)["results"]
                if res:
                    return res[0]
            res = self._get("search/tv", query=name)["results"]
            return res[0] if res else None

        return self._cached(f"t|{name.lower()}|{year or ''}", fetch)
