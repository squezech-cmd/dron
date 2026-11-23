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

echo "########## javac of all sources (collect every error)"
rm -rf /tmp/out && mkdir -p /tmp/out
find src/main/java src/client/java -name '*.java' > /tmp/srcs.txt
javac -proc:none -nowarn -encoding UTF-8 -cp "$CP" -d /tmp/out @/tmp/srcs.txt 2>&1 | head -120
echo "########## javac exit=$?"
echo "########## done"
