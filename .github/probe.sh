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
echo "########## Player methods mentioning Friction/Speed"
javap -p -cp "$CP" net.minecraft.world.entity.player.Player 2>&1 | grep -E "Friction|Speed" | head -20
echo "########## LivingEntity"
javap -p -cp "$CP" net.minecraft.world.entity.LivingEntity 2>&1 | grep -E "Friction|Speed|travel" | head -20
echo "########## Entity"
javap -p -cp "$CP" net.minecraft.world.entity.Entity 2>&1 | grep -E "Friction|Speed" | head -20
echo "########## done"
