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
echo "########## client classpath"
cat /tmp/ccp.txt

CP="$(tr '\n' ':' < /tmp/ccp.txt)"
dump() { echo "########## $1"; javap -p -cp "$CP" "$1" 2>&1; }
g() { echo "########## $1 (grep: $2)"; javap -p -cp "$CP" "$1" 2>&1 | grep -E "$2" | head -50; }

dump net.minecraft.client.player.Input
g net.minecraft.client.Options 'KeyMapping key|boolean hideGui'
g net.minecraft.client.Minecraft 'cameraEntity|crosshairPickEntity|hitResult|player;|level;|font;|screen;|setScreen|getWindow|execute|getInstance|gameRenderer|mouseHandler|options;|playerList|gameMode|isPaused|getSoundManager|getFrameTime|getDeltaFrameTime|smartCull'
g net.minecraft.client.Camera 'setRotation|setPosition|setup|getPosition|getEntity'
g net.minecraft.client.gui.Gui 'render|renderHotbar|renderCrosshair|renderPlayerHealth'
g net.minecraft.client.gui.GuiGraphics 'fill\(|fillGradient|drawString|blit\(|enableScissor|disableScissor|pose\(|drawCenteredString|renderOutline|hLine|vLine|guiWidth|guiHeight'
g net.minecraft.client.gui.Font 'int width|int lineHeight|int draw'
g net.minecraft.client.KeyMapping 'isDown|consumeClick|getKey'
dump net.minecraft.client.resources.sounds.AbstractTickableSoundInstance

echo "########## compile smoke test"
javac -proc:none -nowarn -cp "$CP" -d /tmp/smoke .github/probe-src/ApiSmoke.java 2>&1 | head -60
echo "########## smoke exit above"
echo "########## done"
