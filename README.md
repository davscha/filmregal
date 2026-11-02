# Filmregal

Eigener Film- und Serien-Player für Android-Tablets mit Ordner-Bibliotheken wie bei Plex – ohne Server.

- **Ordner-Bibliotheken:** Jeder Ordner im Startordner auf dem NAS (z. B. Kino, Klassiker, Kinder, Dokus, Anime …) erscheint automatisch in der Seitenleiste – darin werden Filme und Serien automatisch getrennt.
- **Poster & Infos** von TMDB (eigener kostenloser API-Schlüssel nötig).
- **AV1** über den Hardware-Decoder (libVLC), dazu MKV, DTS, AC3 und alle gängigen Untertitel.
- **Weiterschauen**, Fortschrittsbalken und „gesehen“-Haken.
- **Wiedergabelisten:** In einer Bibliothek lange auf einen Film tippen, auf einen Ordner sogar für eine ganze Staffel.
- **Direkt vom NAS** per SMB2/3 (FritzBox: FRITZ.NAS).

## Installieren

1. Auf dem Tablet unter **Releases → latest** die Datei `Filmregal.apk` laden.
2. Öffnen und die Installation aus unbekannten Quellen für den Browser erlauben.
3. Neue Builds lassen sich einfach drüber installieren, Einstellungen bleiben erhalten.

## Einrichten

| Feld | FritzBox-Beispiel |
| --- | --- |
| Adresse | `fritz.box` oder `192.168.178.1` |
| Freigabe | `FRITZ.NAS` |
| Startordner | Der Ordner, in dem deine Bibliotheken liegen, z. B. `Medien` |
| Benutzer / Passwort | FritzBox-Benutzer mit NAS-Rechten |

In der FritzBox muss unter *Heimnetz → USB/Speicher → Heimnetzfreigabe* der Zugriff über SMB aktiv sein.

## Bauen

Jeder Push auf `main` baut über GitHub Actions automatisch ein neues APK.
