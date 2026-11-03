"""Findet Videos und erkennt am Dateinamen, ob es ein Film oder eine Serienfolge ist."""
import re
from dataclasses import dataclass, field

VIDEO_ENDUNGEN = {"mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "wmv", "mpg"}

FOLGE = re.compile(r"S(\d{1,2})[ ._-]?E(\d{1,3})", re.IGNORECASE)        # S01E02, s1e2, S01.E02
JAHR = re.compile(r"(19\d\d|20\d\d)")                                    # 1900 bis 2099
STAFFEL_ORDNER = re.compile(r"^(staffel|season)\s*\d+$", re.IGNORECASE)  # "Staffel 1", "Season 02"
ZUSAETZE = re.compile(r"\b(2160p|1080p|720p|480p|uhd|hdr|x264|x265|h264|h265|hevc|av1|bluray|"
                      r"web-?dl|webrip|german|deutsch|ac3|dts)\b.*", re.IGNORECASE)


@dataclass
class Film:
    titel: str
    jahr: str
    pfad: str


@dataclass
class Folge:
    serie: str
    jahr: str
    staffel: int
    nummer: int
    pfad: str

    @property
    def code(self) -> str:
        return f"S{self.staffel:02d}E{self.nummer:02d}"


@dataclass
class Serie:
    name: str
    jahr: str
    folgen: list[Folge] = field(default_factory=list)

    @property
    def staffeln(self) -> list[int]:
        return sorted({f.staffel for f in self.folgen})


def ist_video(name: str) -> bool:
    return name.rsplit(".", 1)[-1].lower() in VIDEO_ENDUNGEN


def aufraeumen(text: str) -> tuple[str, str]:
    """'Anaconda.1997.German.1080p' → ('Anaconda', '1997')"""
    text = text.replace(".", " ").replace("_", " ")
    jahr = ""
    treffer = [m for m in JAHR.finditer(text) if m.start() > 0]   # Jahr ganz vorn wäre ein Titel wie „2012“
    if treffer:
        jahr = treffer[-1].group(1)
        text = text[: treffer[-1].start()]
    text = ZUSAETZE.sub("", text)
    titel = " ".join(text.split()).strip(" -([")
    return titel or "Unbekannt", jahr


def erkennen(pfad: str) -> Film | Folge:
    teile = pfad.split("/")
    name = teile[-1].rsplit(".", 1)[0]                 # Dateiname ohne Endung
    treffer = FOLGE.search(name)
    if not treffer:
        titel, jahr = aufraeumen(name)
        return Film(titel, jahr, pfad)

    vorne = name[: treffer.start()]
    if vorne.strip(" .-_"):                            # "Sleepy Hollow S01E01"
        serie, jahr = aufraeumen(vorne)
    else:                                              # nur "S01E01" → Name aus dem Ordner
        ordner = [t for t in reversed(teile[:-1]) if not STAFFEL_ORDNER.match(t.strip())]
        serie, jahr = aufraeumen(ordner[0] if ordner else "Unbekannt")
    return Folge(serie, jahr, int(treffer.group(1)), int(treffer.group(2)), pfad)


def durchsuchen(quelle, ordner: str, melden=None) -> list[str]:
    """Sucht in ordner und allen Unterordnern nach Videos und liefert ihre Pfade."""
    videos, offen = [], [ordner]
    while offen:
        aktuell = offen.pop()
        for eintrag in quelle.liste(aktuell):
            if eintrag.ist_ordner:
                offen.append(eintrag.pfad)
            elif ist_video(eintrag.name):
                videos.append(eintrag.pfad)
        if melden:
            melden(len(videos))
    return videos


def sortieren(pfade: list[str]) -> tuple[list[Film], list[Serie]]:
    filme, serien = [], {}
    for pfad in pfade:
        video = erkennen(pfad)
        if isinstance(video, Film):
            filme.append(video)
        else:
            serie = serien.setdefault(video.serie.lower(), Serie(video.serie, video.jahr))
            serie.folgen.append(video)
            serie.jahr = serie.jahr or video.jahr
    for serie in serien.values():
        serie.folgen.sort(key=lambda f: (f.staffel, f.nummer))
    filme.sort(key=lambda f: f.titel.lower())
    return filme, sorted(serien.values(), key=lambda s: s.name.lower())
