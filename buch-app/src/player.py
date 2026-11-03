"""Die Player-Seite."""
import flet as ft
import flet_video as fv


def player_seite(app, pfad: str, titel: str, route: str) -> ft.View:
    ab = app.positionen.get(pfad, 0)
    meldung = ft.Text("", color=ft.Colors.RED_300)

    def fehler(e):
        meldung.value = f"Das Video lässt sich nicht abspielen: {e.data}"
        meldung.update()

    video = fv.Video(
        playlist=[fv.VideoMedia(app.server.adresse(app.quelle, pfad))],
        autoplay=True, expand=True, on_error=fehler,
        # "start" ist eine Einstellung des eingebauten Players mpv: Er beginnt bei dieser Sekunde.
        configuration=fv.VideoConfiguration(mpv_properties={"start": str(int(ab))} if ab else None),
    )

    async def position_merken():
        try:
            position = (await video.get_current_position()).in_seconds
            dauer = (await video.get_duration()).in_seconds
        except Exception:
            return
        if dauer and position > dauer * 0.95:          # fast zu Ende → gilt als gesehen
            app.gesehen.add(pfad)
            app.positionen.pop(pfad, None)
        elif position > 30:
            app.positionen[pfad] = position
        app.merken()

    app.beim_verlassen = position_merken
    return ft.View(
        route=route, padding=0, bgcolor=ft.Colors.BLACK,
        appbar=ft.AppBar(title=ft.Text(titel), bgcolor=ft.Colors.with_opacity(0.5, ft.Colors.BLACK)),
        controls=[ft.Stack([video, ft.Container(meldung, left=20, bottom=20)], expand=True)],
    )
