#!/usr/bin/env bash
# Temporary inspection helper (removed before the final release).
set -uo pipefail
echo "########## gradle build"
chmod +x ./gradlew
./gradlew build --no-daemon -q > /tmp/b.log 2>&1 || tail -30 /tmp/b.log
JAR="$(ls build/libs/dron-*.jar 2>/dev/null | grep -v sources | head -1)"
echo "JAR=$JAR"
echo "########## Player bytecode: friction & riding"
javap -p -c -cp "$JAR" net.minecraft.world.entity.player.Player 2>/dev/null | grep -n -E "friction|Friction|wantsToStopRiding|travel" | head -20
echo "########## decompile-ish: list Player methods via mappings"
grep -rn "getFrictionInfluencedSpeed" "$HOME/.gradle/caches/fabric-loom/1.20.1/layered/working_dir/" 2>/dev/null | head -5
echo "########## mappings entries near friction"
F="$HOME/.gradle/caches/fabric-loom/1.20.1/layered/working_dir/layered_hash_2198/mojang/client.txt"
if [ -f "$F" ]; then
  grep -n -E "Friction" "$F" | head -10
  echo "--- Player (class_1657) block"
  awk '/^net.minecraft.world.entity.player.Player -> /{f=1} f&&/^net\./{if(NR>1&&$0!~/world.entity.player.Player/)exit} f' "$F" | grep -E "Friction|float|Speed" | head -20
fi
echo "########## done"
