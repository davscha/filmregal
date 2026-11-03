"""Erkennt Filme und Serienfolgen am Dateinamen und durchsucht Bibliotheken."""

from __future__ import annotations

import hashlib
import json
import os
import re
import time
from dataclasses import dataclass, field

VIDEO_EXT = {"mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "wmv", "mpg", "mpeg"}

# S01E01, s1e2, S01.E05, 1x02 – führende Nullen sind egal
EPISODE = re.compile(r"(?i)(?:^|[^a-z0-9])S(\d{1,2})[ ._-]?E(\d{1,3})(?![0-9])|(?:^|[^0-9])(\d{1,2})x(\d{2,3})(?![0-9])")
SEASON_DIR = re.compile(r"(?i)^(staffel|season|s)\s*\d+$")
YEAR = re.compile(r"[\[(]?((?:19|20)\d{2})[\])]?")
TAGS = re.compile(
    r"(?i)\b(2160p|1080p|720p|576p|480p|uhd|x264|x265|h ?26[45]|hevc|av1|bluray|blu-ray|bdrip|brrip|"
    r"web-?dl|webrip|remux|german|deutsch|ac3|eac3|dts|truehd|atmos)\b.*"
)


def clean(raw: str, is_dir: bool) -> tuple[str, str | None]:
    """'Vaiana [2026].mp4' -> ('Vaiana', '2026')"""
    s = raw if is_dir else raw.rsplit(".", 1)[0]
    s = s.replace(".", " ").replace("_", " ")
    year = None
    matches = [m for m in YEAR.finditer(s) if m.start() > 0]
    if matches:
        year = matches[-1].group(1)
        s = s[: matches[-1].start()]
    s = TAGS.sub("", s)
    s = re.sub(r"\s+", " ", s).strip(" -([")
    return (s or raw), year


@dataclass
class Movie:
    path: str
    title: str
    year: str | None
    added: float = 0


@dataclass
class Episode:
    path: str
    show: str
    show_year: str | None
    season: int
    number: int
    added: float = 0

    @property
    def code(self) -> str:
        return f"S{self.season:02d}E{self.number:02d}"


@dataclass
class Show:
    name: str
    year: str | None
    episodes: list[Episode] = field(default_factory=list)

    @property
    def seasons(self) -> int:
        return len({e.season for e in self.episodes})


def classify(path: str, added: float = 0) -> Movie | Episode:
    file = path.rsplit("/", 1)[-1]
    base = file.rsplit(".", 1)[0]
    m = EPISODE.search(base)
    if m:
        season = int(m.group(1) or m.group(3))
        number = int(m.group(2) or m.group(4))
        prefix = base[: m.start()]
        if prefix.strip(" .-_"):
            show, year = clean(prefix, is_dir=True)
        else:
            # Nur "S01E01.mkv" → Serienname aus dem Ordner, Staffel-Ordner überspringen
            folders = [f for f in reversed(path.split("/")[:-1]) if not SEASON_DIR.match(f.strip())]
            show, year = clean(folders[0] if folders else "Unbekannt", is_dir=True)
        return Episode(path, show, year, season, number, added)
    title, year = clean(file, is_dir=False)
    return Movie(path, title, year, added)


def natural_key(s: str):
    return [int(t) if t.isdigit() else t for t in re.split(r"(\d+)", s.lower())]


class Library:
    def __init__(self, videos: list, scanned_at: float):
        self.videos = videos
        self.scanned_at = scanned_at

    @property
    def movies(self) -> list[Movie]:
        return sorted((v for v in self.videos if isinstance(v, Movie)), key=lambda m: natural_key(m.title))

    @property
    def shows(self) -> list[Show]:
        groups: dict[str, Show] = {}
        for v in self.videos:
            if isinstance(v, Episode):
                g = groups.setdefault(v.show.lower(), Show(v.show, v.show_year))
                g.episodes.append(v)
                g.year = g.year or v.show_year
        for g in groups.values():
            g.episodes.sort(key=lambda e: (e.season, e.number))
        return sorted(groups.values(), key=lambda s: natural_key(s.name))


class LibraryStore:
    """Scan-Ergebnisse pro Bibliothek als JSON zwischenspeichern."""

    def __init__(self, data_dir: str):
        self.dir = os.path.join(data_dir, "libraries")
        os.makedirs(self.dir, exist_ok=True)

    def _file(self, root: str) -> str:
        return os.path.join(self.dir, "lib_" + hashlib.md5(root.encode()).hexdigest()[:12] + ".json")

    def load(self, root: str) -> Library | None:
        try:
            with open(self._file(root), encoding="utf-8") as f:
                o = json.load(f)
            return Library([classify(p, a) for p, a in o["videos"]], o["scanned_at"])
        except Exception:
            return None

    def scan(self, nas, root: str, progress=None) -> Library:
        videos, pending, folders = [], [root], 0
        while pending:
            folder = pending.pop(0)
            try:
                entries = nas.list(folder)
            except Exception:
                if folder == root:
                    raise
                entries = []
            for e in entries:
                if e.is_dir:
                    pending.append(e.path)
                elif e.name.rsplit(".", 1)[-1].lower() in VIDEO_EXT:
                    videos.append(classify(e.path, e.added))
            folders += 1
            if progress:
                progress(folders, len(videos))
        lib = Library(videos, time.time())
        with open(self._file(root), "w", encoding="utf-8") as f:
            json.dump({"scanned_at": lib.scanned_at, "videos": [[v.path, v.added] for v in videos]}, f)
        return lib
