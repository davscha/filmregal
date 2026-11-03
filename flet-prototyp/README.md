# Filmregal – Python-Prototyp (Flet)

Ein kleiner Nachbau von Filmregal in **Python** mit **[Flet](https://flet.dev)**, um zu vergleichen, wie sich die App in Python anfühlt. Geschrieben von Claude (Anthropic).

Kann bisher:

- NAS per SMB verbinden (`pysmb`, auch SMB1 + NTLMv1 für ältere FritzBoxen)
- Bibliotheken in der Seitenleiste, Scan aller Unterordner mit Zwischenspeicher
- Umschalter **Filme | Serien** – Folgen werden an `S01E01` / `1x02` erkannt
- Poster von TMDB, Filme mit Jahr gesucht
- Raster mit 5 Postern im Querformat, 2 im Hochformat

Noch nicht: Wiedergabe, Infoseite, Suche.

## Dateien

| Datei | Inhalt |
| --- | --- |
| `src/main.py` | Oberfläche (Seitenleiste, Raster, Einstellungen) |
| `src/nas.py` | NAS-Zugriff mit pysmb |
| `src/library.py` | Film/Serien-Erkennung und Scan |
| `src/tmdb.py` | Poster-Suche bei TMDB |

## Am PC ausprobieren

```bash
pip install "flet[all]==1.0.5" pysmb requests
cd flet-prototyp
FILMREGAL_DEMO=1 flet run src/main.py      # mit Beispieldaten, ohne NAS
flet run src/main.py                       # mit echtem NAS
```

Unter Windows (PowerShell): `$env:FILMREGAL_DEMO=1; flet run src/main.py`

## APK

Wird bei jedem Push automatisch gebaut: **Releases → flet-latest → Filmregal-Python.apk**
