#!/usr/bin/env bash
# Startet jedes Konsolenbeispiel einmal und zeigt die Ausgabe.
set -u
LIB="$(pwd)/konsole/build/install/konsole/lib/*"
mkdir -p lauf && cd lauf
fehler=0
starte() {  # $1 = Hauptklasse, $2 = Eingabe
    echo "=================== $1"
    printf '%b' "${2:-}" | timeout 90 java -cp "$LIB" "$1" 2>&1 | head -60
    code=${PIPESTATUS[1]}
    echo "--- Exit-Code: $code"
    [ "$code" != 0 ] && fehler=$((fehler+1))
}
for f in ../quelle/k1_*.kt ../quelle/k2_*.kt; do
    n=$(basename "$f" .kt)
    [ "$n" = k2_12 ] && continue
    klasse="bsp.$n.$(echo "${n^}")Kt"
    case $n in
        k1_05) starte "$klasse" "Anna\n" ;;
        k1_13) starte "$klasse" "n\nLightyear\n2022\ng\nencanto\nn\nTest\nabc\nq\n" ;;
        *) starte "$klasse" ;;
    esac
done
starte de.filmregal.ProgrammKt
echo "Beispiele mit Fehler-Code: $fehler"
