"""Filmregal – Python/Flet-Prototyp.

Seitenleiste mit Bibliotheken vom NAS, Poster-Raster mit Umschalter Filme | Serien.
Zum Ausprobieren am PC ohne NAS:  FILMREGAL_DEMO=1 flet run src/main.py
"""

from __future__ import annotations

import json
import os

import flet as ft

from library import Episode, Library, LibraryStore, Movie, Show
from nas import Entry, Nas, NasConfig
from tmdb import IMG, Tmdb

DATA_DIR = os.getenv("FLET_APP_STORAGE_DATA") or os.path.join(os.path.expanduser("~"), ".filmregal-py")
os.makedirs(DATA_DIR, exist_ok=True)
SETTINGS_FILE = os.path.join(DATA_DIR, "settings.json")
DEMO = os.getenv("FILMREGAL_DEMO") == "1"

BG = "#0B1626"
SURFACE = "#102036"
VARIANT = "#1A2E4A"
SELECTED = "#1F4470"
PRIMARY = "#5AB0FF"
MUTED = "#B8C7DA"


def load_settings() -> dict:
    try:
        with open(SETTINGS_FILE, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {"host": "192.168.178.1", "share": "", "root": "", "user": "", "password": "", "tmdb": ""}


def save_settings(s: dict):
    with open(SETTINGS_FILE, "w", encoding="utf-8") as f:
        json.dump(s, f)


class DemoNas:
    """Spielt ein NAS mit ein paar Beispieldateien vor (FILMREGAL_DEMO=1)."""

    method = "Demo"
    files = {
        "Kino/Filme": ["Die Insel der Abenteuer [2008].mp4", "Reise zum Mittelpunkt der Erde [2009].mp4",
                       "50 erste Dates [2004].mp4", "Bad Moms [2016].mp4", "2012 [2009].mkv",
                       "Anaconda [1997].mp4", "Chaos Walking [2021].mkv"],
        "Kino/Serien/Sleepy Hollow/Staffel 01": [f"Sleepy Hollow S01E{i:02d}.mp4" for i in range(1, 5)],
        "Kino/Serien/Sleepy Hollow/Staffel 02": [f"Sleepy Hollow S02E{i:02d}.mp4" for i in range(1, 3)],
        "Kinder": ["Encanto [2021].mkv", "Lightyear [2022].mkv", "Toy Story 5 [2026].mkv"],
    }

    def list(self, path: str) -> list[Entry]:
        path = path.strip("/")
        result, seen = [], set()
        for folder, names in self.files.items():
            if folder == path:
                result += [Entry(n, f"{folder}/{n}", False, 0) for n in names]
            elif folder.startswith(path + "/") or not path:
                sub = folder[len(path) + 1:] if path else folder
                first = sub.split("/")[0]
                if first not in seen:
                    seen.add(first)
                    result.append(Entry(first, f"{path}/{first}".strip("/"), True))
        return result


def main(page: ft.Page):
    page.title = "Filmregal (Python)"
    page.bgcolor = BG
    page.padding = 0
    page.theme_mode = ft.ThemeMode.DARK
    page.theme = ft.Theme(color_scheme_seed=PRIMARY)
    page.dark_theme = ft.Theme(color_scheme_seed=PRIMARY)

    state = {"settings": load_settings(), "screen": "", "mode": "filme", "libs": [], "gen": 0, "grid": None}
    store = LibraryStore(DATA_DIR)

    def make_services():
        s = state["settings"]
        state["nas"] = DemoNas() if DEMO else Nas(NasConfig(s["host"], s["share"], s["root"], s["user"], s["password"]))
        state["tmdb"] = Tmdb(DATA_DIR, s.get("tmdb", ""))

    make_services()

    def columns() -> int:
        # Querformat 5 Poster pro Zeile, Hochformat 2
        w, h = page.width or 1600, page.height or 1000
        return 5 if w >= h else 2

    def on_resize(e):
        grid = state.get("grid")
        if grid is not None:
            grid.runs_count = columns()
            grid.update()

    page.on_resize = on_resize

    # ------------------------------------------------------------ Seitenleiste

    lib_column = ft.Column(spacing=2)
    sidebar_error = ft.Text("", color=ft.Colors.RED_200, size=12, visible=False)
    content = ft.Container(expand=True, padding=ft.Padding.only(left=28, right=28, top=20))

    def nav_item(label: str, icon, key: str, on_click):
        selected = state["screen"] == key
        return ft.Container(
            content=ft.Row([ft.Icon(icon, color=PRIMARY if selected else MUTED), ft.Text(label, size=16)], spacing=14),
            padding=ft.Padding.symmetric(horizontal=18, vertical=12),
            border_radius=28,
            bgcolor=SELECTED if selected else None,
            on_click=on_click,
        )

    def refresh_sidebar():
        lib_column.controls = [
            nav_item(e.name[:1].upper() + e.name[1:], ft.Icons.FOLDER, f"lib:{e.path}",
                     lambda ev, p=e.path: show_library(p))
            for e in state["libs"]
        ]
        settings_item.content = nav_item("Einstellungen", ft.Icons.SETTINGS, "settings", lambda e: show_settings())
        page.update()

    settings_item = ft.Container()
    sidebar = ft.Container(
        width=250,
        bgcolor=SURFACE,
        padding=ft.Padding.symmetric(horizontal=12, vertical=20),
        content=ft.Column(
            [
                ft.Container(ft.Text("Filmregal", size=26, weight=ft.FontWeight.BOLD, color=PRIMARY),
                             padding=ft.Padding.only(left=12, bottom=4)),
                ft.Container(ft.Text("Python-Prototyp", size=12, color=MUTED), padding=ft.Padding.only(left=12, bottom=12)),
                ft.Container(ft.Text("Bibliotheken", size=13, color=PRIMARY), padding=ft.Padding.only(left=12, top=8)),
                lib_column,
                ft.Container(sidebar_error, padding=ft.Padding.symmetric(horizontal=12)),
                ft.Container(ft.Text("Mehr", size=13, color=PRIMARY), padding=ft.Padding.only(left=12, top=16)),
                settings_item,
            ],
            scroll=ft.ScrollMode.AUTO,
            spacing=4,
        ),
    )

    def load_libraries():
        def work():
            try:
                entries = state["nas"].list(state["settings"]["root"])
                state["libs"] = [e for e in entries if e.is_dir]
                sidebar_error.visible = False
            except Exception as ex:
                state["libs"] = []
                sidebar_error.value = f"Keine Verbindung zum NAS:\n{ex}"
                sidebar_error.visible = True
            refresh_sidebar()
            if state["libs"] and not state["screen"]:
                show_library(state["libs"][0].path)

        page.run_thread(work)

    # ------------------------------------------------------------ Poster-Kacheln

    def poster_card(title: str, sub: str, icon, on_click):
        box = ft.Container(
            content=ft.Column(
                [ft.Icon(icon, size=48, color=PRIMARY),
                 ft.Text(title, text_align=ft.TextAlign.CENTER, max_lines=4, overflow=ft.TextOverflow.ELLIPSIS)],
                alignment=ft.MainAxisAlignment.CENTER,
                horizontal_alignment=ft.CrossAxisAlignment.CENTER,
            ),
            bgcolor=VARIANT,
            border_radius=12,
            padding=12,
            expand=True,
            alignment=ft.Alignment.CENTER,
        )
        title_text = ft.Text(title, size=14, text_align=ft.TextAlign.CENTER, max_lines=2, overflow=ft.TextOverflow.ELLIPSIS)
        sub_text = ft.Text(sub, size=12, color=MUTED, text_align=ft.TextAlign.CENTER)
        card = ft.Container(
            content=ft.Column([box, title_text, sub_text], spacing=6, horizontal_alignment=ft.CrossAxisAlignment.CENTER),
            border_radius=12,
            on_click=on_click,
        )
        card.data = (box, title_text, sub_text)
        return card

    def set_poster(card, info: dict | None, sub_override: str | None = None):
        if not info:
            return
        box, title_text, sub_text = card.data
        if info.get("poster"):
            box.content = None
            box.padding = 0
            box.image = ft.DecorationImage(src=IMG + info["poster"], fit=ft.BoxFit.COVER)
        if info.get("title"):
            title_text.value = info["title"]
        if sub_override is None and info.get("year"):
            sub_text.value = info["year"]
        try:
            card.update()
        except Exception:
            pass

    def show_info(title: str, sub: str, info: dict | None):
        page.show_dialog(
            ft.AlertDialog(
                title=ft.Text((info or {}).get("title") or title),
                content=ft.Container(
                    ft.Text(((info or {}).get("overview") or "Keine Beschreibung.") + f"\n\n{sub}", size=14),
                    width=520,
                ),
                actions=[ft.TextButton("Schließen", on_click=lambda e: page.pop_dialog())],
            )
        )

    # ------------------------------------------------------------ Bibliothek

    def show_library(root: str):
        state["screen"] = f"lib:{root}"
        state["gen"] += 1
        gen = state["gen"]
        refresh_sidebar()

        title = ft.Text(root.rsplit("/", 1)[-1], size=26)
        progress = ft.ProgressBar(visible=False, color=PRIMARY)
        info_text = ft.Text("", size=12, color=MUTED)
        grid = ft.GridView(expand=True, runs_count=columns(), child_aspect_ratio=0.56, spacing=18, run_spacing=22,
                           padding=ft.Padding.only(bottom=24))
        state["grid"] = grid
        segment = ft.SegmentedButton(
            segments=[ft.Segment(value="filme", label=ft.Text("Filme")),
                      ft.Segment(value="serien", label=ft.Text("Serien"))],
            selected=[state["mode"]],
        )
        lib_holder = {"lib": None}

        def fill():
            lib: Library | None = lib_holder["lib"]
            if lib is None:
                return
            movies, shows = lib.movies, lib.shows
            # Beim Öffnen automatisch dahin wechseln, wo es Inhalte gibt
            if not lib_holder.get("auto_done"):
                lib_holder["auto_done"] = True
                if state["mode"] == "serien" and not shows and movies:
                    state["mode"] = "filme"
                elif state["mode"] == "filme" and not movies and shows:
                    state["mode"] = "serien"
                segment.selected = [state["mode"]]
            segment.segments[0].label = ft.Text(f"Filme ({len(movies)})")
            segment.segments[1].label = ft.Text(f"Serien ({len(shows)})")
            cards = []
            if state["mode"] == "serien":
                for s in shows:
                    sub = f"{s.seasons} {'Staffel' if s.seasons == 1 else 'Staffeln'} · {len(s.episodes)} Folgen"
                    c = poster_card(s.name, sub, ft.Icons.TV,
                                    lambda e, s=s, sub=sub: show_info(s.name, sub, state["tmdb"].show(s.name, s.year)))
                    cards.append((c, s))
            else:
                for m in movies:
                    c = poster_card(m.title, m.year or "", ft.Icons.MOVIE,
                                    lambda e, m=m: show_info(m.title, m.path.rsplit("/", 1)[-1],
                                                             state["tmdb"].movie(m.title, m.year)))
                    cards.append((c, m))
            grid.controls = [c for c, _ in cards]
            info_text.value = "" if cards else ("Keine Serien gefunden." if state["mode"] == "serien" else "Keine Filme gefunden.")
            page.update()

            def load_posters():
                for c, item in cards:
                    if state["gen"] != gen:
                        return  # andere Seite geöffnet
                    if isinstance(item, Show):
                        set_poster(c, state["tmdb"].show(item.name, item.year), sub_override=c.data[2].value)
                    else:
                        set_poster(c, state["tmdb"].movie(item.title, item.year))

            page.run_thread(load_posters)

        def on_mode(e):
            state["mode"] = e.control.selected[0]
            fill()

        segment.on_change = on_mode

        def scan(e=None):
            def work():
                progress.visible = True
                info_text.value = "Durchsuche Bibliothek …"
                page.update()

                def prog(folders, videos):
                    info_text.value = f"{folders} Ordner durchsucht, {videos} Videos gefunden"
                    info_text.update()

                try:
                    lib_holder["lib"] = store.scan(state["nas"], root, prog)
                    info_text.value = ""
                except Exception as ex:
                    info_text.value = f"Durchsuchen fehlgeschlagen: {ex}"
                progress.visible = False
                page.update()
                if state["gen"] == gen:
                    fill()

            page.run_thread(work)

        content.content = ft.Column(
            [
                ft.Row([title, ft.Container(expand=True), segment,
                        ft.IconButton(ft.Icons.REFRESH, tooltip="Neu durchsuchen", on_click=scan)]),
                progress,
                info_text,
                grid,
            ],
            expand=True,
        )
        page.update()

        lib_holder["lib"] = store.load(root)
        if lib_holder["lib"] is None:
            scan()
        else:
            fill()

    # ------------------------------------------------------------ Einstellungen

    def show_settings():
        state["screen"] = "settings"
        state["grid"] = None
        state["gen"] += 1
        refresh_sidebar()
        s = state["settings"]
        host = ft.TextField(label="Adresse", value=s["host"], helper="z. B. 192.168.178.1")
        share = ft.TextField(label="Freigabe", value=s["share"], helper="Leer lassen, wenn der Startordner mit der Freigabe beginnt")
        root = ft.TextField(label="Startordner", value=s["root"], helper="Ordner, in dem deine Bibliotheken liegen")
        user = ft.TextField(label="Benutzername", value=s["user"])
        password = ft.TextField(label="Passwort", value=s["password"], password=True, can_reveal_password=True)
        tmdb = ft.TextField(label="TMDB-API-Schlüssel", value=s.get("tmdb", ""), helper="Für Poster – kostenlos auf themoviedb.org")
        status = ft.Text("", selectable=True)

        def current() -> dict:
            return {"host": host.value.strip(), "share": share.value.strip().strip("/"),
                    "root": root.value.strip().strip("/"), "user": user.value.strip(),
                    "password": password.value, "tmdb": tmdb.value.strip()}

        def save(e):
            state["settings"] = current()
            save_settings(state["settings"])
            make_services()
            state["screen"] = ""
            status.value = "Gespeichert."
            page.update()
            load_libraries()

        def test(e):
            status.value = "Verbinde …"
            page.update()

            def work():
                c = current()
                n = DemoNas() if DEMO else Nas(NasConfig(c["host"], c["share"], c["root"], c["user"], c["password"]))
                try:
                    dirs = [x.name for x in n.list(c["root"]) if x.is_dir]
                    status.value = f"✓ Verbunden ({n.method}). {len(dirs)} Ordner: {', '.join(dirs)}"
                except Exception as ex:
                    status.value = f"✗ {ex}"
                page.update()

            page.run_thread(work)

        content.content = ft.Column(
            [
                ft.Text("Einstellungen", size=26),
                ft.Container(
                    ft.Column([host, share, root, user, password, tmdb,
                               ft.Row([ft.FilledButton("Speichern", on_click=save),
                                       ft.OutlinedButton("Verbindung testen", on_click=test)]),
                               status], spacing=14),
                    width=640,
                ),
            ],
            scroll=ft.ScrollMode.AUTO,
            expand=True,
        )
        page.update()

    page.add(ft.Row([sidebar, content], expand=True, spacing=0))
    refresh_sidebar()
    if DEMO or state["settings"].get("host"):
        load_libraries()
    if not DEMO and not state["settings"].get("root") and not state["settings"].get("share"):
        show_settings()


if __name__ == "__main__":
    if os.getenv("FILMREGAL_WEB") == "1":
        ft.run(main, view=ft.AppView.WEB_BROWSER, port=int(os.getenv("PORT", "8550")), no_cdn=True)
    else:
        ft.run(main)
