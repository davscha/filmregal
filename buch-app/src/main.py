"""Filmregal – die eigene Film- und Serien-App. Start im Projektordner mit:  flet run"""
import flet as ft

import speicher
from einstellungen import einstellungen_seite
from infoseiten import film_seite, serie_seite
from player import player_seite
from quelle import quelle_erstellen
from regal import RegalSeite
from streamserver import StreamServer
from tmdb import Tmdb


class Filmregal:
    """Hält alles zusammen, was die Seiten der App gemeinsam brauchen."""

    def __init__(self, page: ft.Page):
        self.page = page
        self.einstellungen = speicher.einstellungen_laden()
        self.positionen = speicher.laden("positionen.json", {})   # Pfad → Sekunden
        self.gesehen = set(speicher.laden("gesehen.json", []))    # Pfade
        self.server = StreamServer()
        self.regale = []          # die Ordner in der obersten Ebene, z. B. ["Kinder", "Kino"]
        self.regal = ""           # das gerade gewählte Regal
        self.ansicht = "filme"    # "filme" oder "serien"
        self.filme, self.serien = [], []
        self.beim_verlassen = None
        self.verbinden()

    def verbinden(self):
        """Nach dem Start und nach jeder Änderung der Einstellungen."""
        self.quelle = quelle_erstellen(self.einstellungen)
        self.tmdb = Tmdb(self.einstellungen["tmdb_schluessel"])
        self.regal_seite = RegalSeite(self)

    def merken(self):
        speicher.speichern("positionen.json", self.positionen)
        speicher.speichern("gesehen.json", sorted(self.gesehen))

    # ---------- Seitenwechsel ----------

    def route_geaendert(self):
        teile = self.page.route.strip("/").split("/")
        views = [self.regal_seite.view]
        if teile[0] == "einstellungen":
            views.append(einstellungen_seite(self))
        elif teile[0] == "film" and len(teile) >= 2:
            nummer = int(teile[1])
            views.append(film_seite(self, nummer))
            if len(teile) == 3:                                    # /film/3/spielen
                film = self.filme[nummer]
                views.append(player_seite(self, film.pfad, film.titel, self.page.route))
        elif teile[0] == "serie" and len(teile) >= 2:
            nummer = int(teile[1])
            views.append(serie_seite(self, nummer))
            if len(teile) == 4:                                    # /serie/2/spielen/5
                folge = self.serien[nummer].folgen[int(teile[3])]
                views.append(player_seite(self, folge.pfad, f"{folge.serie} {folge.code}", self.page.route))
        self.page.views.clear()
        self.page.views.extend(views)
        self.page.update()

    async def zurueck(self, e: ft.ViewPopEvent):
        if self.beim_verlassen:                                    # z. B. Position im Film merken
            await self.beim_verlassen()
            self.beim_verlassen = None
        self.page.views.pop()
        await self.page.push_route(self.page.views[-1].route)


def main(page: ft.Page):
    page.title = "Filmregal"
    page.theme_mode = ft.ThemeMode.DARK
    page.theme = ft.Theme(color_scheme_seed=ft.Colors.BLUE)
    app = Filmregal(page)
    page.on_route_change = lambda e: app.route_geaendert()
    page.on_view_pop = app.zurueck
    page.on_resize = lambda e: app.regal_seite.spalten_anpassen()
    app.route_geaendert()
    app.regal_seite.regale_laden()


ft.run(main)
