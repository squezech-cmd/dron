#!/usr/bin/env bash
# Temporary inspection helper (removed before the final release).
set -uo pipefail

cat > /tmp/cp.gradle <<'EOF'
allprojects {
    tasks.register("printCompileCp") {
        doLast {
            configurations.compileClasspath.each { println it }
        }
    }
}
EOF
./gradlew -q -I /tmp/cp.gradle printCompileCp > /tmp/cp.txt 2>/dev/null || true
echo "########## compile classpath"
cat /tmp/cp.txt

MCJAR="$(grep -oE '/[^ ]*minecraft[^ ]*\.jar' /tmp/cp.txt | head -1)"
echo "MCJAR=$MCJAR"
if [ -z "$MCJAR" ] || [ ! -f "$MCJAR" ]; then
  MCJAR="$(find "$HOME/.gradle/caches/fabric-loom" -name '*.jar' -print0 2>/dev/null \
    | xargs -0 -I{} sh -c 'unzip -l "{}" 2>/dev/null | grep -q "net/minecraft/world/entity/Entity.class" && echo "{}"' \
    | head -3 | tail -1)"
  echo "MCJAR(fallback)=$MCJAR"
fi
if [ -z "$MCJAR" ] || [ ! -f "$MCJAR" ]; then
  echo "no named minecraft jar found"
  find "$HOME/.gradle/caches/fabric-loom" -name '*.jar' | sed "s|$HOME|~|" | head -40
  exit 0
fi

dump() {
  echo "########## $1"
  javap -p -cp "$MCJAR" "$1" 2>&1
}
grep_dump() {
  echo "########## $1 (grep: $2)"
  javap -p -cp "$MCJAR" "$1" 2>&1 | grep -E "$2" | head -40
}

dump net.minecraft.client.player.Input
dump net.minecraft.client.Camera
dump net.minecraft.client.KeyMapping
dump net.minecraft.world.level.Explosion
grep_dump net.minecraft.client.Minecraft 'cameraEntity|CameraEntity|options;|player;|level;|font;|hitResult|screen;|setScreen|getWindow|execute|getInstance|playerList|gameMode|gameRenderer|mouseHandler|isPaused'
grep_dump net.minecraft.client.Options 'KeyMapping key|hideGui|sensitivity'
grep_dump net.minecraft.client.gui.GuiGraphics 'fill|drawString|blit|Scissor|pose|drawCenteredString|renderOutline|renderTooltip|setColor|drawTexture'
grep_dump net.minecraft.client.gui.Font 'width|lineHeight|draw' 
grep_dump net.minecraft.world.level.Level 'explode|getEntities|noCollision|playSound|isClientSide|hasChunkAt|getBlockStates|isLoaded|clip'
grep_dump net.minecraft.server.level.ServerLevel 'sendParticles|playSound'
grep_dump net.minecraft.world.entity.Entity 'hurt|setPos|getBoundingBox|DeltaMovement|noPhysics|discard|isRemoved|canBeCollidedWith|isPickable|getFirstPassenger|hasPassenger|blockPosition|getPosition\(|setYRot|setXRot|getLookAngle|isNoGravity|setNoGravity|shouldRenderAtSqrDistance|isAttackable|canChangeDimensions|getEyePosition|kill\(|isSpectator|isPushable|getVehicle|startRiding|stopRiding|onPassengerTurned|positionRider|getControllingPassenger|isPassenger|isVehicle|setDeltaMovement|getViewVector|getRotationVector'
grep_dump net.minecraft.world.entity.player.Player 'Input input|getGameProfile|displayClientMessage|isSpectator|isCreative|abilities|mayfly|flying|getAbilities|stopSleeping|getVehicle|getYRot'
grep_dump net.minecraft.client.player.LocalPlayer 'input|isControlledByLocalInstance|getYRot|sendPosition'
grep_dump net.minecraft.world.phys.AABB 'deflate|inflate|move|contains|getCenter|expandTowards|intersects'
grep_dump net.minecraft.world.phys.Vec3 'scale|add|length|normalize|distanceTo|dot|cross|subtract|multiply|horizontalDistance'
grep_dump net.minecraft.util.Mth 'clamp\(|lerp\(|rotLerp|wrapDegrees|lerpInt'
grep_dump net.minecraft.world.item.ItemStack 'is\(|getItem|getOrCreateTag|getTag'
grep_dump net.minecraft.world.item.Item 'Properties|use\(|useOn|isEnchantable|m_'
echo "########## Explosion ctor"
javap -p -cp "$MCJAR" net.minecraft.world.level.Explosion 2>&1 | grep -E "Explosion\(|explode|finalize"
echo "########## ParticleTypes"
javap -p -cp "$MCJAR" net.minecraft.core.particles.ParticleTypes 2>&1 | grep -E "EXPLOSION|SMOKE|FLAME|ELECTRIC|CRIT|LAVA|ASH|END_ROD|FIREWORK|SONIC|POOF" | head -20
echo "########## done"
