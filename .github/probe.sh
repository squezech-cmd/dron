#!/usr/bin/env bash
# Temporary inspection helper (removed before the final release).
set -uo pipefail

cat > /tmp/cp.gradle <<'EOF'
allprojects {
    tasks.register("printCompileCp") {
        doLast { configurations.compileClasspath.each { println it } }
    }
}
EOF
./gradlew -q -I /tmp/cp.gradle printCompileCp > /tmp/cp.txt 2>/dev/null || true
CLIENT="$(grep -E 'minecraft-client-only[^ ]*\.jar' /tmp/cp.txt | head -1)"
COMMON="$(grep -E 'minecraft-common[^ ]*\.jar' /tmp/cp.txt | head -1)"
CP="$CLIENT:$COMMON"
echo "CLIENT=$CLIENT"

dump() { echo "########## $1"; javap -p -cp "$CP" "$1" 2>&1; }
g() { echo "########## $1 (grep: $2)"; javap -p -cp "$CP" "$1" 2>&1 | grep -E "$2" | head -50; }

dump net.minecraft.client.player.Input
g net.minecraft.client.Options 'KeyMapping key|boolean hideGui|OptionInstance<java.lang.Integer> fov|sensitivity|invertYMouse'
g net.minecraft.client.Minecraft 'cameraEntity|hitResult|crosshairPickEntity|player;|level;|font;|screen;|setScreen|getWindow|execute|getInstance|gameRenderer|mouseHandler|options;|playerList|gameMode|isPaused|startAttack|continueAttack|getSoundManager|getFrameTime|getDeltaFrameTime'
g net.minecraft.client.Camera 'setRotation|setPosition|setup|rotation|forwards|up;|left;|getPosition|getLookVector|getXRot|getYRot|getEntity'
g net.minecraft.client.gui.Gui 'void render|renderHotbar|renderCrosshair|renderPlayerHealth|renderExperienceBar|renderItemHotbar|renderEffects|renderBossOverlay|renderDebug'
g net.minecraft.client.gui.GuiGraphics 'fill\(|fillGradient|drawString|blit\(|enableScissor|disableScissor|pose\(|drawCenteredString|renderOutline|setColor|hLine|vLine|drawTexture|bufferSource|flush'
g net.minecraft.client.gui.Font 'int width|int lineHeight|int draw|drawInBatch'
g net.minecraft.client.KeyMapping 'isDown|consumeClick|getKey|setDown|isUnbound|matches|getCategory|saveString|click'
g net.minecraft.client.resources.sounds.AbstractTickableSoundInstance 'AbstractTickableSoundInstance\(|isStopped|tick\(|stop\(|canPlaySound|updateVolume|updatePitch'
g net.minecraft.client.resources.sounds.SoundInstance 'isLooping|getDelay|getVolume|getPitch|isRelative|Attenuation|getLocation|canStartSilent|getSource'
g net.minecraft.client.resources.sounds.SimpleSoundInstance 'forEntity|forUI|forLocalAmbience|getInstance'
g net.minecraft.client.sounds.SoundManager 'play\(|stop\(|isActive'
g net.minecraft.world.entity.Entity 'getYRot|getXRot|getViewVector|getEyePosition|position\(|getPosition\(|distanceTo|getBoundingBox|isRemoved|getId|getType|yRotO|xRotO|zo|getEntityData|getData|setData|isInvisible|isSpectator|isAlive|level\(|getDeltaMovement|setDeltaMovement|isPassenger|getVehicle|discard|kill|getBbHeight|getBbWidth|getForward'
echo "########## EntityDataSerializers"
g net.minecraft.network.syncher.EntityDataSerializers 'INT|BOOLEAN|FLOAT|OPTIONAL_UUID|BYTE|STRING|UUID' | head
echo "########## Entity data methods"
g net.minecraft.world.entity.Entity 'SynchedEntityData|getEntityData|tick\(|tickCount|setPos|getId|stringUUID|blockPosition|positionCodec|getAddEntityPacket|hasImpulse|isNoGravity|setNoGravity|onGround|horizontalCollision|verticalCollision|getPassengers|isVehicle'
echo "########## ExplosionInteraction values"
g 'net.minecraft.world.level.Level$ExplosionInteraction' 'NONE|BLOCK|MOB|TNT|ExplosionInteraction'
echo "########## ClipContext"
dump net.minecraft.world.level.ClipContext
dump 'net.minecraft.world.level.ClipContext$Block'
dump 'net.minecraft.world.level.ClipContext$Fluid'
g net.minecraft.world.level.Level 'clip\(|getBlockState|isEmptyBlock'
g net.minecraft.world.phys.HitResult 'getType|getLocation|Type|isInside'
echo "########## DamageSource"
g net.minecraft.world.damagesource.DamageSources 'explosion|playerAttack|generic|magic'
echo "########## ServerPlayer"
g net.minecraft.server.level.ServerPlayer 'getYRot|getXRot|displayClientMessage|connection|getServer|teleportTo|setCamera|hurt'
echo "########## Item"
g net.minecraft.world.item.Item 'use\(|getName|Properties|isEnchantable|verifyTagAfterLoad'
echo "########## Fabric"
for c in \
 net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents \
 net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback \
 net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking \
 net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking \
 net.fabricmc.fabric.api.networking.v1.PacketByteBufs \
 net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup \
 net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper \
 net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry \
 net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry \
 net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry \
 net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents \
 net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry ; do
  f=$(grep -oE '/[^ ]*(fabric-[^ /]*)\/[^ ]*\.jar' /tmp/cp.txt | head -40)
  echo "########## $c"
  javap -p -cp "$(echo "$CP"; echo "$(grep -oE '/[^ ]*\.jar' /tmp/cp.txt | tr '\n' ':')" | tr '\n' ':')" "$c" 2>&1 | head -40
done
echo "########## done"
