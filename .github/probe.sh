#!/usr/bin/env bash
# Temporary inspection helper (deleted before the final release).
set -uo pipefail

mkdir -p /tmp/art/x
JAR="$(ls /tmp/art/x/*.jar 2>/dev/null | grep -v sources | head -1)"
if [ -z "${JAR}" ]; then
  echo "no jar downloaded"
  exit 0
fi
echo "JAR=$JAR"

echo "########## jar entries"
unzip -l "$JAR" | head -80

echo "########## refmap"
unzip -p "$JAR" dron-refmap.json || echo "NO REFMAP"

echo "########## javap"
for C in \
  net.minecraft.client.Minecraft \
  net.minecraft.client.player.LocalPlayer \
  net.minecraft.client.player.Input \
  net.minecraft.client.KeyMapping \
  net.minecraft.world.entity.player.Player \
  net.minecraft.world.entity.LivingEntity \
  net.minecraft.world.entity.Entity \
  net.minecraft.client.gui.Gui ; do
  echo "########## $C"
  javap -p -cp "$JAR" "$C" 2>&1
done

echo "########## reflection"
cat > /tmp/Probe.java <<'JAVA'
import java.lang.reflect.*;

public class Probe {
    static void dump(Class<?> c, String rx) {
        System.out.println("=== " + c.getName());
        for (Field f : c.getDeclaredFields()) {
            if (rx == null || f.getName().matches(rx)) {
                System.out.println("  FIELD " + Modifier.toString(f.getModifiers()) + " "
                        + f.getType().getSimpleName() + " " + f.getName());
            }
        }
        for (Method m : c.getDeclaredMethods()) {
            if (rx != null && m.getName().matches(rx)) {
                System.out.println("  METHOD " + m.getName() + " " + java.util.Arrays.toString(m.getParameterTypes())
                        + " -> " + m.getReturnType().getSimpleName());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader cl = Probe.class.getClassLoader();
        for (String n : new String[] {
                "net.minecraft.world.entity.Entity",
                "net.minecraft.client.KeyMapping",
                "net.minecraft.client.Minecraft",
                "net.minecraft.client.player.Input",
                "net.minecraft.client.player.LocalPlayer" }) {
            try {
                dump(Class.forName(n, false, cl), null);
            } catch (Throwable t) {
                System.out.println("!! " + n + " -> " + t);
            }
        }
    }
}
JAVA
javac -proc:none -cp "$JAR" -d /tmp/pc /tmp/Probe.java && java -cp "$JAR:/tmp/pc" Probe
echo "########## done"
