"""Die Startseite: Seitenleiste mit den Regalen, Umschalter Filme | Serien, Poster-Raster."""
import flet as ft

import speicher
from erkennen import durchsuchen, sortieren


def auffrischen(control: ft.Control):
    """Zeigt Änderungen an – aber nur, wenn das Control gerade auf dem Bildschirm ist."""
    try:
        control.update()
    except RuntimeError:
        pass                                             # (noch) nicht zu sehen – macht nichts


class RegalSeite:
    """Seitenleiste mit den Regalen, Umschalter Filme | Serien und das Poster-Raster."""

    def __init__(self, app):
        self.app = app
        self.lauf = 0                      # zählt mit, damit alte Poster-Ladevorgänge aufhören
        self.leiste = ft.NavigationRail(
            selected_index=0, label_type=ft.NavigationRailLabelType.ALL, min_width=100,
            destinations=[], visible=False, on_change=self.regal_gewaehlt,
        )
        self.umschalter = ft.SegmentedButton(
            selected=[app.ansicht], on_change=self.ansicht_gewechselt,
            segments=[ft.Segment(value="filme", label=ft.Text("Filme"), icon=ft.Icons.MOVIE),
                      ft.Segment(value="serien", label=ft.Text("Serien"), icon=ft.Icons.TV)],
        )
        self.titel = ft.Text("Filmregal", size=28, weight=ft.FontWeight.BOLD)
        self.status = ft.Text("", color=ft.Colors.GREY_400)
        self.raster = ft.GridView(expand=True, runs_count=5, child_aspect_ratio=0.56,
                                  spacing=18, run_spacing=18)
        kopf = ft.Row([
            self.titel, self.umschalter, ft.Container(expand=True),
            ft.IconButton(ft.Icons.REFRESH, tooltip="Neu durchsuchen",
                          on_click=lambda e: app.page.run_thread(self.regal_lesen, True)),
            ft.IconButton(ft.Icons.SETTINGS, tooltip="Einstellungen",
                          on_click=lambda e: app.page.navigate("/einstellungen")),
        ], spacing=16)
        inhalt = ft.Container(ft.Column([kopf, self.status, self.raster], expand=True),
                              expand=True, padding=20)
        self.view = ft.View(route="/", padding=0,
                            controls=[ft.Row([self.leiste, inhalt], expand=True, spacing=0)])
        self.spalten_anpassen()

    def spalten_anpassen(self):
        """5 Poster nebeneinander im Querformat, 2 im Hochformat."""
        breite, hoehe = self.app.page.width, self.app.page.height
        if breite and hoehe:
            self.raster.runs_count = 5 if breite >= hoehe else 2
            auffrischen(self.raster)

    def regale_laden(self):
        self.app.page.run_thread(self._regale_laden)

    def _regale_laden(self):
        self.status.value = "Verbinde …"
        auffrischen(self.view)
        try:
            ordner = [e.name for e in self.app.quelle.liste("") if e.ist_ordner]
        except Exception as fehler:
            self.status.value = f"Keine Verbindung: {fehler}  –  Prüfe die Einstellungen (Zahnrad oben rechts)."
            auffrischen(self.view)
            return
        self.app.regale = ordner or [""]               # ohne Unterordner: alles ist ein einziges Regal
        self.leiste.destinations = [
            ft.NavigationRailDestination(icon=ft.Icons.FOLDER_OUTLINED, selected_icon=ft.Icons.FOLDER,
                                         label=name)
            for name in ordner
        ]
        self.leiste.visible = len(ordner) >= 2           # eine Leiste mit nur einem Eintrag wäre sinnlos
        self.leiste.selected_index = 0
        self.app.regal = self.app.regale[0]
        self.regal_lesen()

    def regal_gewaehlt(self, e):
        self.app.regal = self.app.regale[self.leiste.selected_index]
        self.app.page.run_thread(self.regal_lesen)

    def ansicht_gewechselt(self, e):
        self.app.ansicht = self.umschalter.selected[0]
        self.raster_fuellen()

    def regal_lesen(self, neu: bool = False):
        """Liest die Videos des gewählten Regals – aus dem Zwischenspeicher oder frisch von der Quelle."""
        app = self.app
        merkname = f"{app.quelle.kennung}|{app.regal}"
        gemerkt = speicher.laden("regale.json", {})
        if neu or merkname not in gemerkt:
            def melden(anzahl):
                self.status.value = f"Durchsuche „{app.regal or 'Videos'}“ … {anzahl} Videos gefunden"
                auffrischen(self.status)
            try:
                gemerkt[merkname] = durchsuchen(app.quelle, app.regal, melden)
            except Exception as fehler:
                self.status.value = f"Fehler beim Durchsuchen: {fehler}"
                auffrischen(self.view)
                return
            speicher.speichern("regale.json", gemerkt)
        app.filme, app.serien = sortieren(gemerkt[merkname])
        self.raster_fuellen()

    def raster_fuellen(self):
        app = self.app
        self.lauf += 1
        self.titel.value = app.regal or "Filmregal"
        aufgaben = []                                    # was danach Poster bekommen soll
        kacheln = []
        if app.ansicht == "filme":
            for nummer, film in enumerate(app.filme):
                kachel, bildplatz = poster_kachel(film.titel, film.jahr, f"/film/{nummer}", app)
                kacheln.append(kachel)
                aufgaben.append(("movie", film.titel, film.jahr, bildplatz))
            self.status.value = f"{len(app.filme)} Filme" if len(app.filme) != 1 else "1 Film"
        else:
            for nummer, serie in enumerate(app.serien):
                staffeln = len(serie.staffeln)
                zeile = f"{staffeln} Staffel" if staffeln == 1 else f"{staffeln} Staffeln"
                kachel, bildplatz = poster_kachel(serie.name, zeile, f"/serie/{nummer}", app)
                kacheln.append(kachel)
                aufgaben.append(("tv", serie.name, serie.jahr, bildplatz))
            self.status.value = f"{len(app.serien)} Serien" if len(app.serien) != 1 else "1 Serie"
        self.raster.controls = kacheln
        auffrischen(self.view)
        app.page.run_thread(self.poster_laden, self.lauf, aufgaben)

    def poster_laden(self, lauf: int, aufgaben: list):
        """Läuft im Hintergrund: holt nacheinander die Poster und setzt sie in die Kacheln."""
        for art, titel, jahr, bildplatz in aufgaben:
            if lauf != self.lauf:                        # inzwischen anderes Regal gewählt
                return
            info = self.app.tmdb.info(art, titel, jahr)
            if info and info["poster"]:
                bildplatz.content = ft.Image(src=info["poster"], fit=ft.BoxFit.COVER,
                                             width=float("inf"), height=float("inf"))
                auffrischen(bildplatz)


def poster_kachel(titel: str, zeile: str, ziel: str, app) -> tuple[ft.Control, ft.Container]:
    """Eine Kachel: Poster oben, Titel und Jahr darunter. Liefert auch den Platz für das Poster."""
    bildplatz = ft.Container(
        content=ft.Icon(ft.Icons.MOVIE, size=48, color=ft.Colors.GREY_600),
        alignment=ft.Alignment.CENTER, bgcolor=ft.Colors.with_opacity(0.08, ft.Colors.WHITE),
        border_radius=12, clip_behavior=ft.ClipBehavior.ANTI_ALIAS, expand=True,
    )
    kachel = ft.Container(
        content=ft.Column([
            bildplatz,
            ft.Text(titel, weight=ft.FontWeight.BOLD, max_lines=1, overflow=ft.TextOverflow.ELLIPSIS),
            ft.Text(zeile, size=12, color=ft.Colors.GREY_400),
        ], spacing=4),
        on_click=lambda e: app.page.navigate(ziel), ink=True, border_radius=12,
    )
    return kachel, bildplatz
