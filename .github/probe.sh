#!/usr/bin/env bash
# Temporary inspection helper (removed before the final release).
set -uo pipefail

cat > /tmp/cp.gradle <<'EOF'
allprojects {
    tasks.register("printClientCp") {
        doLast {
            def ss = project.extensions.findByName('sourceSets')
            if (ss != null && ss.findByName('client') != null) {
                ss.client.compileClasspath.each { println it }
            }
        }
    }
}
EOF
./gradlew -q -I /tmp/cp.gradle printClientCp > /tmp/ccp.txt 2>/dev/null || true
CP="$(tr '\n' ':' < /tmp/ccp.txt)"

g() { echo "########## $1 (grep: $2)"; javap -p -cp "$CP" "$1" 2>&1 | grep -E "$2" | head -40; }
dump() { echo "########## $1"; javap -p -cp "$CP" "$1" 2>&1 | head -60; }

g net.minecraft.client.renderer.GameRenderer 'renderItemInHand|getMainCamera|getFov|renderLevel|extractRenderState'
dump net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
dump net.minecraft.client.resources.sounds.SoundInstance
g net.minecraft.client.resources.sounds.SoundInstance 'Attenuation|enum'
g net.minecraft.world.entity.Entity 'getEyeHeight|positionRider|onPassengerTurned|canBeHitByProjectile|getPassengerAttachmentPoint|setBoundingBox|refreshDimensions|onSyncedDataUpdated|isVehicle|getRootVehicle|isLocalPlayer|isEffectiveAi|isAlwaysTicking|broadcastToPlayer'
g net.minecraft.world.entity.LivingEntity 'getEyeHeight|isDeadOrDying|travel|getViewYRot|getViewXRot|getYRot|getXRot|isBlocking|hurt'
g net.minecraft.server.level.ServerPlayer 'teleportTo|stopRiding|setGameMode|connection|onUpdateAbilities'
g net.minecraft.world.item.Item 'Properties|use\(|finishUsingItem|getUseDuration'
g net.minecraft.world.item.CreativeModeTab 'builder|title|icon|displayItems|build|Builder'
g net.minecraft.world.entity.EntityType\$Builder 'sized|clientTrackingRange|updateInterval|fireImmune|noSummon|build|of\('
g net.minecraft.network.syncher.SynchedEntityData 'defineId|define\(|get\(|set\('
g net.minecraft.world.level.Level 'getEntitiesOfClass|getNearestPlayer|getPlayerByUUID|getServer|players\(|dimension|getEntities\('
g net.minecraft.world.level.Explosion 'Explosion\(|explode|finalizeExplosion'
g com.mojang.blaze3d.vertex.PoseStack 'scale|translate|mulPose|pushPose|popPose'
g com.mojang.math.Axis 'rotationDegrees|rotation'
g net.minecraft.client.renderer.RenderType 'entityCutoutNoCull|entitySolid|entityTranslucent|entityCutout'
g net.minecraft.client.model.EntityModel 'renderToBuffer|setupAnim|renderType'
g net.minecraft.client.model.geom.ModelPart 'getChild|render|xRot|yRot|zRot|visible|resetPose|skipDraw'
g net.minecraft.world.phys.Vec3 'horizontalDistance|normalize|lengthSqr'
g net.minecraft.core.particles.ParticleTypes 'EXPLOSION|LARGE_SMOKE|FLAME|ELECTRIC_SPARK|CRIT|SMOKE|END_ROD|ASH|FIREWORK|SONIC_BOOM|POOF|Lava'
g net.minecraft.sounds.SoundEvents 'EXPLODE|FIREWORK|UI_BUTTON_CLICK|NOTE_BLOCK|ARROW|GENERIC_EXPLODE|TRIDENT|ELYTRA|BEE|BAT|SNOWBALL'
g net.minecraft.network.chat.ChatFormatting 'RED|GREEN|GRAY|DARK_GRAY|GOLD|YELLOW|WHITE|AQUA|BOLD|ITALIC'
echo "########## done"
