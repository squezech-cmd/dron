#!/usr/bin/env bash
# Temporary inspection helper (removed before the final release).
set -uo pipefail

CACHE="$HOME/.gradle/caches/fabric-loom/1.20.1"
CLIENT="$(find "$CACHE" -maxdepth 1 -name 'minecraft-client-only.jar' | head -1)"
COMMON="$(find "$CACHE" -maxdepth 1 -name 'minecraft-common.jar' | head -1)"
CP="$CLIENT:$COMMON"
echo "CLIENT=$CLIENT"
echo "COMMON=$COMMON"

dump() {
  echo "########## $1"
  javap -p -cp "$CP" "$1" 2>&1
}

dump net.minecraft.client.player.Input
dump net.minecraft.client.KeyMapping
dump net.minecraft.client.Camera
dump net.minecraft.client.gui.GuiGraphics
dump net.minecraft.client.gui.Font
dump net.minecraft.world.level.Explosion
dump net.minecraft.world.entity.EntityType\$Builder
dump net.minecraft.sounds.SoundEvent
dump net.minecraft.world.entity.Entity

echo "########## Minecraft (filtered)"
javap -p -cp "$CP" net.minecraft.client.Minecraft 2>&1 | grep -E "cameraEntity|CameraEntity|options;|player;|level;|font;|hitResult|screen;|setScreen|getWindow|execute|getInstance|playerList|gameMode|gameRenderer" | head -30

echo "########## Options (keys)"
javap -p -cp "$CP" net.minecraft.client.Options 2>&1 | grep -E "keyUp|keyDown|keyLeft|keyRight|keyJump|keyShift|keySprint|hideGui|sensitivity" | head -20

echo "########## Gui"
javap -p -cp "$CP" net.minecraft.client.gui.Gui 2>&1 | grep -E "void render|renderHotbar|renderCrosshair|renderPlayerHealth|renderEffects|renderDebug" | head -20

echo "########## LocalPlayer input"
javap -p -cp "$CP" net.minecraft.client.player.LocalPlayer 2>&1 | grep -E "Input|input|isControlledByLocalInstance|getYRot" | head -10
echo "########## Player input/profile"
javap -p -cp "$CP" net.minecraft.world.entity.player.Player 2>&1 | grep -E "Input input|getGameProfile|displayClientMessage|isSpectator|isCreative|abilities" | head -10

echo "########## Level explode / misc"
javap -p -cp "$CP" net.minecraft.world.level.Level 2>&1 | grep -E "explode|getEntities|noCollision|playSound|isClientSide|hasChunkAt|getBlockStates|isLoaded" | head -40

echo "########## ExplosionInteraction"
javap -p -cp "$CP" net.minecraft.world.level.Level\$ExplosionInteraction 2>&1 | head -20
echo "########## Explosion.BlockInteraction"
javap -p -cp "$CP" net.minecraft.world.level.Explosion\$BlockInteraction 2>&1 | head -20

echo "########## ServerLevel particles/sound"
javap -p -cp "$CP" net.minecraft.server.level.ServerLevel 2>&1 | grep -E "sendParticles|playSound" | head -20

echo "########## Entity misc"
javap -p -cp "$CP" net.minecraft.world.entity.Entity 2>&1 | grep -E "hurt|setPos|position\(|getBoundingBox|DeltaMovement|noPhysics|discard|isRemoved|canBeCollidedWith|isPickable|EyeHeight|getFirstPassenger|hasPassenger|getDirection|blockPosition|getPosition\(|setYRot|setXRot|getLookAngle|level\(|isNoGravity|setNoGravity|shouldRenderAtSqrDistance|isAttackable|canChangeDimensions|getEyePosition|kill\(" | head -60

echo "########## AABB / Vec3"
javap -p -cp "$CP" net.minecraft.world.phys.AABB 2>&1 | grep -E "deflate|inflate|move|contains|getCenter|expandTowards" | head -20
javap -p -cp "$CP" net.minecraft.world.phys.Vec3 2>&1 | grep -E "scale|add|length|normalize|distanceTo|dot|cross|subtract|multiply" | head -20

echo "########## Mth"
javap -p -cp "$CP" net.minecraft.util.Mth 2>&1 | grep -E "clamp\(|lerp\(|rotLerp|wrapDegrees" | head -10

echo "########## Component / ChatFormatting"
javap -p -cp "$CP" net.minecraft.network.chat.Component 2>&1 | grep -E "translatable|literal|empty" | head -10

echo "########## ClientLevel entity"
javap -p -cp "$CP" net.minecraft.client.multiplayer.ClientLevel 2>&1 | grep -E "getEntity" | head -5

echo "########## Fabric API jars"
find "$HOME/.gradle/caches/modules-2" -name 'fabric-item-group-api*.jar' -o -name 'fabric-networking-api-v1*.jar' 2>/dev/null | head -5
echo "########## done"
