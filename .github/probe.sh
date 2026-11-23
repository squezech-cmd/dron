#!/usr/bin/env bash
# Temporary inspection helper (removed before the final release).
set -uo pipefail

CACHE="$HOME/.gradle/caches/fabric-loom"
echo "########## loom cache"
find "$CACHE" -maxdepth 3 -type f \( -name '*.jar' -o -name '*.txt' \) 2>/dev/null | sed "s|$HOME|~|" | head -40

MC="$(find "$CACHE" -name 'minecraft-merged-*.jar' 2>/dev/null | grep -v sources | head -1)"
MAPS="$(find "$CACHE" -name 'client.txt' 2>/dev/null | head -1)"
echo "MC=$MC"
echo "MAPS=$MAPS"

echo "########## mappings grep"
if [ -n "$MAPS" ]; then
  for pat in wantsToStopRiding getFrictionInfluencedSpeed 'void hurt(' startRiding startSeenByPlayer stopSeenByPlayer isControlledByLocalInstance noCollision 'void setPos(' isSpectator 'playLocalSound' 'void explode(' finalizeExplosion; do
    echo "--- $pat"
    grep -n -F "$pat" "$MAPS" | head -6
  done
  echo "--- class_1657 (Player) header"
  grep -n -F "net.minecraft.world.entity.player.Player ->" "$MAPS" | head -3
  echo "--- field names of LocalPlayer"
  awk '/^net.minecraft.client.player.LocalPlayer -> /{f=1;print;next} f&&/^net\./{exit} f' "$MAPS" | head -40
fi

echo "########## javap (official-named merged jar)"
if [ -n "$MC" ]; then
  for C in net.minecraft.world.entity.Entity net.minecraft.world.entity.player.Player \
           net.minecraft.client.gui.Gui net.minecraft.client.Camera net.minecraft.world.level.Level \
           net.minecraft.world.level.Explosion net.minecraft.client.renderer.entity.EntityRenderer; do
    echo "########## $C"
    javap -p -cp "$MC" "$C" 2>&1 | grep -E "wantsToStopRiding|Friction|hurt\(|startRiding|SeenByPlayer|isControlledByLocalInstance|noCollision|setPos\(|isSpectator|playLocalSound|explode\(|finalizeExplosion|setRotation|setPosition|setup\(|render\(|shadow|setYRot|setXRot|setNoGravity|positionRider|defineSynchedData|InteractionResult interact" | head -30
  done
  echo "########## Input"
  javap -p -cp "$MC" net.minecraft.client.player.Input 2>&1 | head -30
  echo "########## Minecraft fields/methods"
  javap -p -cp "$MC" net.minecraft.client.Minecraft 2>&1 | grep -E "player;|level;|options;|font;|getWindow|hitResult|pick\(|gameMode|setScreen|getInstance|playerList|screen;" | head -20
  echo "########## LocalPlayer"
  javap -p -cp "$MC" net.minecraft.client.player.LocalPlayer 2>&1 | head -45
  echo "########## AbstractClientPlayer/Player fields"
  javap -p -cp "$MC" net.minecraft.client.player.AbstractClientPlayer 2>&1 | grep -E "input|isSpectator|isCreative" | head -10
  javap -p -cp "$MC" net.minecraft.world.entity.player.Player 2>&1 | grep -E "input|abilities|isSpectator|isCreative|blockActionRestricted|endCurrentMining|drop\(" | head -20
fi

echo "########## built jar mixin annotations"
JAR="$(ls build/libs/dron-*.jar 2>/dev/null | grep -v sources | head -1)"
echo "JAR=$JAR"
if [ -n "$JAR" ]; then
  unzip -l "$JAR" | grep -E "refmap|mixins.json|fabric.mod.json"
  unzip -p "$JAR" dron.mixins.json
  echo "--- PlayerMixin annotation values"
  javap -v -p -cp "$JAR" com.squezech.dron.mixin.PlayerMixin 2>&1 | grep -E "wantsTo|method_|Inject|value=|s=" | head -20
fi
echo "########## done"
