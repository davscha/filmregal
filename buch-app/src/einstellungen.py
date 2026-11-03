"""Die Einstellungsseite."""
import flet as ft

import speicher
from quelle import NasQuelle


def einstellungen_seite(app) -> ft.View:
    e = app.einstellungen
    quelle = ft.RadioGroup(value=e["quelle"], content=ft.Row([
        ft.Radio(value="ordner", label="Ordner auf diesem Gerät"),
        ft.Radio(value="nas", label="NAS im Heimnetz"),
    ]))
    ordner = ft.TextField(label="Ordner", value=e["ordner"], hint_text=r"z. B. C:\Users\Name\Testregal")
    adresse = ft.TextField(label="Adresse des NAS", value=e["nas_adresse"], hint_text="z. B. 192.168.178.1")
    benutzer = ft.TextField(label="Benutzer", value=e["nas_benutzer"])
    passwort = ft.TextField(label="Passwort", value=e["nas_passwort"], password=True, can_reveal_password=True)
    nas_ordner = ft.TextField(label="Ordner auf dem NAS", value=e["nas_ordner"],
                              hint_text="beginnt mit der Freigabe, z. B. FRITZ.NAS/USB-Platte/Filme")
    ergebnis = ft.Text("")
    nas_felder = ft.Column([adresse, benutzer, passwort, nas_ordner,
                            ft.Row([ft.OutlinedButton("Verbindung testen", icon=ft.Icons.WIFI_FIND,
                                                      on_click=lambda _: app.page.run_thread(testen))]),
                            ergebnis], spacing=12, horizontal_alignment=ft.CrossAxisAlignment.STRETCH)
    schluessel = ft.TextField(label="TMDB-API-Schlüssel", value=e["tmdb_schluessel"], password=True,
                              can_reveal_password=True)

    def sichtbarkeit():
        ordner.visible = quelle.value == "ordner"
        nas_felder.visible = quelle.value == "nas"

    def quelle_gewechselt(_):
        sichtbarkeit()
        app.page.update()

    def testen():
        ergebnis.value = "Verbinde …"
        ergebnis.update()
        try:
            oben = NasQuelle(adresse.value, benutzer.value, passwort.value)
            freigaben = ", ".join(f.name for f in oben.liste(""))
            zeilen = [f"OK: Angemeldet. Freigaben auf dem NAS: {freigaben}"]
            if nas_ordner.value.strip():
                darin = NasQuelle(adresse.value, benutzer.value, passwort.value, nas_ordner.value).liste("")
                regale = ", ".join(f.name for f in darin if f.ist_ordner) or "(keine Unterordner)"
                zeilen.append(f"OK: Ordner gefunden. Darin: {regale}")
            else:
                zeilen.append("Trage jetzt bei „Ordner auf dem NAS“ den Weg zu deinen Filmen ein, "
                              "beginnend mit einer dieser Freigaben.")
            ergebnis.value = "\n".join(zeilen)
        except Exception as fehler:
            ergebnis.value = f"Fehler: {fehler}"
        ergebnis.update()

    def speichern(_):
        e.update(quelle=quelle.value, ordner=ordner.value.strip(), nas_adresse=adresse.value.strip(),
                 nas_benutzer=benutzer.value.strip(), nas_passwort=passwort.value,
                 nas_ordner=nas_ordner.value.strip().strip("/"), tmdb_schluessel=schluessel.value.strip())
        speicher.speichern("einstellungen.json", e)
        app.verbinden()
        app.page.navigate("/")
        app.regal_seite.regale_laden()

    quelle.on_change = quelle_gewechselt
    sichtbarkeit()
    return ft.View(
        route="/einstellungen", padding=30, scroll=ft.ScrollMode.AUTO,
        appbar=ft.AppBar(title=ft.Text("Einstellungen")),
        controls=[ft.Column([
            ft.Text("Woher kommen die Videos?", size=20, weight=ft.FontWeight.BOLD),
            quelle, ordner, nas_felder,
            ft.Text("Poster und Infos", size=20, weight=ft.FontWeight.BOLD),
            schluessel,
            ft.Text("Kostenlos bei themoviedb.org, dort unter Einstellungen > API", size=12, color=ft.Colors.GREY_400),
            ft.Row([ft.FilledButton("Speichern", icon=ft.Icons.SAVE, on_click=speichern)]),
        ], spacing=16, width=600, horizontal_alignment=ft.CrossAxisAlignment.STRETCH)],
    )
