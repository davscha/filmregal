"""Kopiert die eBook-Beispiele in die beiden Module und setzt je ein eigenes Paket davor."""
import pathlib, re, shutil, sys
quelle = pathlib.Path(sys.argv[1])
hier = pathlib.Path(__file__).parent
ziele = {"konsole": hier / "konsole/src/main/kotlin", "app": hier / "app/src/main/java"}
for z in ziele.values():
    shutil.rmtree(z, ignore_errors=True)
for datei in sorted(quelle.rglob("*.kt")):
    name = datei.stem if datei.parent == quelle else datei.parent.name
    modul = "app" if name.startswith("k3") or name == "k2_12" else "konsole"
    text = datei.read_text(encoding="utf-8")
    if not re.search(r"^package ", text, re.M):
        text = f"package bsp.{name}\n\n" + text
    ziel = ziele[modul] / "bsp" / name / datei.name
    ziel.parent.mkdir(parents=True, exist_ok=True)
    ziel.write_text(text, encoding="utf-8")
    print(modul, ziel.relative_to(hier))
