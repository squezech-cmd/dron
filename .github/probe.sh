#!/usr/bin/env bash
set -uo pipefail
cat > /tmp/cp.gradle <<'GRADLE'
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
GRADLE
./gradlew -q -I /tmp/cp.gradle printClientCp > /tmp/ccp.txt 2>/dev/null || true
CP="$(tr '\n' ':' < /tmp/ccp.txt)"
echo "########## Entity lerp/pos methods"
javap -p -cp "$CP" net.minecraft.world.entity.Entity 2>&1 | grep -E "lerp|setOldPos|getInterpolation|snapTo|absMoveTo|moveTo" | head -20
echo "########## EntityType builder + datafixer"
javap -p -cp "$CP" net.minecraft.world.entity.EntityType 2>&1 | grep -E "getKey|builtInRegistryHolder|toString" | head -10
echo "########## Blocks beacon"
javap -p -cp "$CP" net.minecraft.world.level.block.Blocks 2>&1 | grep -E "BEACON" | head -3
echo "########## BlockPos distSqr/offset"
javap -p -cp "$CP" net.minecraft.core.BlockPos 2>&1 | grep -E "distSqr|offset\(|betweenClosed|distManhattan" | head -12
echo "########## DataFixers"
javap -p -cp "$CP" net.minecraft.util.datafix.DataFixers 2>&1 | grep -E "addFixer|getDataFixer" | head -5
echo "########## ServerPlayer fallDistance"
javap -p -cp "$CP" net.minecraft.world.entity.Entity 2>&1 | grep -E "fallDistance|resetFallDistance|setOnGround" | head -5
echo "########## Player startRiding & dismount"
javap -p -cp "$CP" net.minecraft.world.entity.player.Player 2>&1 | grep -E "startRiding|stopRiding|isShiftKeyDown|getYRot|getXRot|getLookAngle|getViewYRot" | head -10
echo "########## done"
