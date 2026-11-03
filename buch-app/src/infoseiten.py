"""Die Infoseiten für Filme und Serien."""
import flet as ft


def zeit(sekunden: float) -> str:
    """3725 → '1:02:05'"""
    s = int(sekunden)
    return f"{s // 3600}:{s // 60 % 60:02d}:{s % 60:02d}" if s >= 3600 else f"{s // 60}:{s % 60:02d}"


def info_seite(route: str, info: dict, titel: str, zeile: str, unten: list[ft.Control]) -> ft.View:
    """Gemeinsamer Aufbau von Film- und Serienseite: Hintergrundbild, Poster, Text, darunter beliebiges."""
    if info.get("poster"):
        bild = ft.Image(src=info["poster"], fit=ft.BoxFit.COVER)
    else:
        bild = ft.Icon(ft.Icons.MOVIE, size=64)
    poster = ft.Container(bild, width=240, height=360, border_radius=14, alignment=ft.Alignment.CENTER,
                          bgcolor=ft.Colors.with_opacity(0.08, ft.Colors.WHITE),
                          clip_behavior=ft.ClipBehavior.ANTI_ALIAS)
    if info.get("bewertung"):
        zeile += f"  ·  Bewertung {info['bewertung']}"
    texte = ft.Column([
        ft.Text(info.get("titel") or titel, size=40, weight=ft.FontWeight.BOLD),
        ft.Text(zeile, size=16, color=ft.Colors.GREY_300),
        ft.Text(info.get("text") or "Keine Beschreibung vorhanden.", size=16, width=700),
        *unten,
    ], spacing=16, expand=True)
    ebenen = []
    if info.get("hintergrund"):                         # großes Bild, abgedunkelt, hinter allem
        ebenen.append(ft.Image(src=info["hintergrund"], fit=ft.BoxFit.COVER, opacity=0.25,
                               left=0, top=0, right=0, bottom=0))
    ebenen.append(ft.Column([
        ft.Row([poster, texte], spacing=40, vertical_alignment=ft.CrossAxisAlignment.START),
    ], scroll=ft.ScrollMode.AUTO, left=40, top=20, right=40, bottom=0))
    return ft.View(
        route=route, padding=0,
        appbar=ft.AppBar(title=ft.Text(titel), bgcolor=ft.Colors.TRANSPARENT),
        controls=[ft.Stack(ebenen, expand=True)],
    )


def film_seite(app, nummer: int) -> ft.View:
    film = app.filme[nummer]
    info = app.tmdb.info("movie", film.titel, film.jahr) or {}
    ab = app.positionen.get(film.pfad, 0)
    spielen = f"/film/{nummer}/spielen"

    def von_vorn(e):
        app.positionen.pop(film.pfad, None)
        app.page.navigate(spielen)

    if ab:
        knoepfe = [ft.FilledButton(f"Weiterschauen ab {zeit(ab)}", icon=ft.Icons.PLAY_ARROW,
                                   on_click=lambda e: app.page.navigate(spielen)),
                   ft.OutlinedButton("Von vorn", icon=ft.Icons.REPLAY, on_click=von_vorn)]
    else:
        knoepfe = [ft.FilledButton("Abspielen", icon=ft.Icons.PLAY_ARROW,
                                   on_click=lambda e: app.page.navigate(spielen))]
    zeile = info.get("jahr") or film.jahr or "Jahr unbekannt"
    if film.pfad in app.gesehen:
        zeile += "  ·  gesehen"
    return info_seite(f"/film/{nummer}", info, film.titel, zeile, [ft.Row(knoepfe)])


def serie_seite(app, nummer: int) -> ft.View:
    serie = app.serien[nummer]
    info = app.tmdb.info("tv", serie.name, serie.jahr) or {}
    folgen_liste = ft.Column(spacing=0)

    def staffel_zeigen(staffel: int):
        folgen_liste.controls.clear()
        for index, folge in enumerate(serie.folgen):
            if folge.staffel != staffel:
                continue
            ab = app.positionen.get(folge.pfad, 0)
            if folge.pfad in app.gesehen:
                symbol, unterzeile = ft.Icons.CHECK_CIRCLE, "gesehen"
            elif ab:
                symbol, unterzeile = ft.Icons.PAUSE_CIRCLE, f"weiter ab {zeit(ab)}"
            else:
                symbol, unterzeile = ft.Icons.PLAY_CIRCLE_OUTLINE, folge.pfad.rsplit("/", 1)[-1]
            folgen_liste.controls.append(ft.ListTile(
                leading=ft.Icon(symbol), title=ft.Text(f"Folge {folge.nummer}"),
                subtitle=ft.Text(unterzeile, size=12),
                on_click=lambda e, i=index: app.page.navigate(f"/serie/{nummer}/spielen/{i}"),
            ))

    def staffel_gewaehlt(e):
        staffel_zeigen(int(auswahl.value))
        folgen_liste.update()

    auswahl = ft.Dropdown(
        label="Staffel", width=200, value=str(serie.staffeln[0]), on_select=staffel_gewaehlt,
        options=[ft.DropdownOption(key=str(s), text=f"Staffel {s}") for s in serie.staffeln],
    )
    staffel_zeigen(serie.staffeln[0])
    zeile = f"{info.get('jahr') or serie.jahr or 'Jahr unbekannt'}  ·  {len(serie.folgen)} Folgen"
    return info_seite(f"/serie/{nummer}", info, serie.name, zeile, [auswahl, folgen_liste])
