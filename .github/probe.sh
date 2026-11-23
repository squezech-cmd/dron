#!/usr/bin/env bash
# Temporary inspection helper (removed before the final release).
set -uo pipefail

echo "########## gradle build"
chmod +x ./gradlew
./gradlew build --no-daemon 2>&1 | tail -40

echo "########## jar"
JAR="$(ls build/libs/dron-*.jar 2>/dev/null | grep -v sources | head -1)"
echo "JAR=$JAR"
unzip -l "$JAR" | tail -40

echo "########## mixin refmap"
unzip -p "$JAR" dron-refmap.json 2>/dev/null | head -60 || echo "(no refmap)"
unzip -p "$JAR" dron.client-refmap.json 2>/dev/null | head -40 || echo "(no client refmap)"

echo "########## remapped PlayerMixin"
javap -v -p -cp "$JAR" com.squezech.dron.mixin.PlayerMixin 2>&1 | grep -E "method_|Inject|s=|value=" | head -20

echo "########## remapped client mixins"
for C in CameraMixin GameRendererMixin GuiMixin; do
  echo "--- $C"
  javap -v -p -cp "$JAR" com.squezech.dron.client.mixin.$C 2>&1 | grep -E "method_|Inject|s=|value=" | head -20
done

echo "########## fabric.mod.json"
unzip -p "$JAR" fabric.mod.json | head -50
echo "########## done"
