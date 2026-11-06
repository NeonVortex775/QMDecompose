#!/usr/bin/env bash
# Plain javac + jar. No Maven, no Gradle, nothing to download.
#
#   bash build.sh              build only
#   bash build.sh install      build and copy into $MC_SERVER/plugins
#
# Dependencies (see lib/README.md):
    lib/spigot-api.jar     required
    lib/dragoncore.jar     required
    lib/placeholderapi.jar required
set -e
cd "$(dirname "$0")"

JAR_NAME="QingMingDecompose.jar"
OUT="build"

for f in spigot-api.jar dragoncore.jar placeholderapi.jar; do
    [ -f "lib/$f" ] || { echo "missing dependency: lib/$f"; exit 1; }
done

rm -rf "$OUT/classes"
mkdir -p "$OUT/classes"

find src/main/java -name '*.java' | sort > "$OUT/sources.txt"
echo "compiling $(wc -l < "$OUT/sources.txt") files"

javac -encoding UTF-8 -source 1.8 -target 1.8 -nowarn \
      -cp "lib/*" -d "$OUT/classes" @"$OUT/sources.txt"

cp -f src/main/resources/*.yml "$OUT/classes/" 2>/dev/null || true
for d in shops gui tables; do
    if [ -d "src/main/resources/$d" ]; then
        mkdir -p "$OUT/classes/$d"
        cp -f src/main/resources/$d/* "$OUT/classes/$d/" 2>/dev/null || true
    fi
done

rm -f "$OUT/$JAR_NAME"
( cd "$OUT/classes" && jar cfe "../$JAR_NAME" com.qingming.decompose.DecomposePlugin . )
echo "built build/$JAR_NAME"
ls -lh "$OUT/$JAR_NAME"

if [ "$1" = "install" ]; then
    SERVER="${MC_SERVER:-}"
    [ -n "$SERVER" ] || { echo "MC_SERVER is not set"; exit 1; }
    # Overwriting a jar while the server is up leaves the old classes already
    # loaded and breaks every class that was not touched yet. Write .new instead.
    if tasklist 2>/dev/null | grep -qiE "(^|[[:space:]])java(w)?\.exe[[:space:]]"; then
        cp -f "$OUT/$JAR_NAME" "$SERVER/plugins/$JAR_NAME.new"
        echo "server is running - wrote plugins/$JAR_NAME.new, move it after shutdown"
    else
        cp -f "$OUT/$JAR_NAME" "$SERVER/plugins/$JAR_NAME"
        echo "copied to $SERVER/plugins/$JAR_NAME"
    fi
fi
