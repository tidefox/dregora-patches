#!/usr/bin/env bash
# Boots a real Forge 1.12.2 dedicated server with FermiumBooter + Dregora Patches
# (+ the patched mods, if given), runs "dpatch mixins", the stats commands, /trickshots,
# "dpatch reload" and "dpatch debug" on the console, stops it, and fails on mixin errors/exceptions.
# Boots with FermiumBooter sorting before and after our jar, with the patched mods;
# then once without them (late configs must skip quietly).
#
# usage: ci/smoke.sh <server-dir> <dregorapatches.jar> <fermiumbooter.jar> [patched-mod.jar ...]
#   <server-dir> must contain an installed Forge server (forge-*.jar + libraries/).
#   patched mods: NuclearCraft 2.19a, Level Up! 2 1.1.23 and Lycanites Mobs 2.0.8.10 (the server's jars).
set -uo pipefail

# Resolve everything to absolute paths before we cd into the server dir.
DIR="$(readlink -f "$1")"; DP="$(readlink -f "$2")"; FERMIUM="$(readlink -f "$3")"
shift 3
EXTRA=()
for f in "$@"; do EXTRA+=("$(readlink -f "$f")"); done
HERE="$(cd "$(dirname "$0")" && pwd)"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-600}"

for f in "$DP" "$FERMIUM" "${EXTRA[@]}"; do
  [ -f "$f" ] || { echo "::error::missing jar: $f"; exit 1; }
done
[ -d "$DIR" ] || { echo "::error::missing server dir: $DIR"; exit 1; }

run_once() { # <file name for the FermiumBooter jar in mods/> <with|without> patched mods
cd "$DIR"
rm -rf mods logs world config crash-reports
mkdir -p mods
cp "$FERMIUM" "mods/$1" && cp "$DP" mods/ || { echo "::error::could not copy jars into mods/"; exit 1; }
local want=2 mods_in=0
if [ "$2" = with ] && [ "${#EXTRA[@]}" -gt 0 ]; then
  cp "${EXTRA[@]}" mods/ || { echo "::error::could not copy patched mods into mods/"; exit 1; }
  want=$((2 + ${#EXTRA[@]})); mods_in=1
fi
[ "$(ls mods/*.jar | wc -l)" -eq "$want" ] || { echo "::error::expected exactly $want jars in mods/"; ls -la mods; exit 1; }
echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
level-type=FLAT
# ocean biome: no spawn biome found, so spawn (and the loaded spawn chunks) stay at 0,0 for the setblocks below
generator-settings=3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;0
generate-structures=false
spawn-npcs=false
spawn-animals=false
spawn-monsters=false
view-distance=3
max-tick-time=-1
server-port=25565
snooper-enabled=false
motd=dregora-patches smoke test
PROPS

FORGE_JAR=$(ls forge-1.12.2-*.jar | grep -v installer | head -1)
echo "Starting $FORGE_JAR with mods: $(ls mods | tr '\n' ' ')"

rm -f stdin.fifo; mkfifo stdin.fifo
java -Xms1G -Xmx2G -Dfml.queryResult=confirm -jar "$FORGE_JAR" nogui < stdin.fifo > console.log 2>&1 &
PID=$!
exec 3> stdin.fifo   # keep the pipe open for the server's lifetime

LOG=console.log
wait_for() { # pattern timeout
  local t=0
  while ! grep -qE "$1" "$LOG" 2>/dev/null; do
    if ! kill -0 "$PID" 2>/dev/null; then echo "::error::server exited while waiting for: $1"; return 1; fi
    sleep 2; t=$((t+2))
    if [ "$t" -ge "$2" ]; then echo "::error::timed out after ${2}s waiting for: $1"; return 1; fi
  done
}

status=0
if wait_for 'Done \([0-9.]+s\)! For help' "$BOOT_TIMEOUT"; then
  echo "Server is up; running 'dpatch mixins', stats commands, 'dpatch reload' and 'dpatch debug'"
  echo "dpatch mixins" >&3
  wait_for '\[dregorapatches\]: /dpatch mixins:' 60 || status=1
  if [ "$mods_in" = 1 ]; then
    # Frozen worldgen-style parts: a port and a controller placed in the spawn chunk (FLAT world).
    echo "setblock 0 4 0 nuclearcraft:fission_port" >&3
    echo "setblock 2 4 0 nuclearcraft:fission_controller_new_idle" >&3
    echo "setblock 4 4 0 minecraft:furnace" >&3
    sleep 3
  fi
  echo "dpatch nc stats" >&3
  wait_for 'guard: [0-9]+ chunk load' 60 || status=1
  echo "dpatch levelup2 stats" >&3
  wait_for 'Level Up! 2 furnace scan' 60 || status=1
  echo "dpatch lycanites stats" >&3
  wait_for 'Lycanites pet recall' 60 || status=1
  echo "reactor admin list" >&3
  wait_for '\[Reactor\] No claimed reactors' 60 || status=1
  echo "dpatch chunkloads 2" >&3
  wait_for 'chunkloads: chunkloads over' 60 || status=1
  echo "dpatch reload" >&3
  wait_for '\[dregorapatches\]: Config reloaded' 60 || status=1
  echo "dpatch debug kill 1" >&3
  wait_for 'Logging the next 1 deaths caused by a player' 60 || status=1
  echo "dpatch debug off" >&3
  wait_for 'Debug probe off' 60 || status=1
  echo "trickshots" >&3
  wait_for 'No trick shots yet' 60 || status=1
else
  status=1
fi

echo "stop" >&3
for _ in $(seq 1 90); do kill -0 "$PID" 2>/dev/null || break; sleep 2; done
if kill -0 "$PID" 2>/dev/null; then echo "::error::server did not stop"; kill -9 "$PID"; status=1; fi
exec 3>&-
wait "$PID" 2>/dev/null

echo "---------------- checks ----------------"
ALL=$(mktemp)
cat console.log logs/latest.log 2>/dev/null > "$ALL"

require() {
  if grep -qE "$1" "$ALL"; then echo "ok:   $2"; else echo "::error::missing: $2 ($1)"; status=1; fi
}
require '\[dregorapatches\]: Enqueued mixins\.dregorapatches\.vanilla\.json \(early' "dregorapatches enqueued its mixin configs"
require 'mixins\.dregorapatches\.vanilla\.json' "FermiumBooter saw the early config"
require 'mixins\.dregorapatches\.nc\.json' "FermiumBooter saw the NC late config"
require 'mixins\.dregorapatches\.levelup2\.json' "FermiumBooter saw the Level Up! 2 late config"
require '\[dregorapatches\]: Mixin config loaded: io\.github\.tidefox\.dregorapatches\.mixin\.vanilla' "Mixin loaded the early (vanilla) config"
require '\[dregorapatches\]: Mixin config loaded: io\.github\.tidefox\.dregorapatches\.mixin\.nc' "Mixin loaded the NC late config"
require '\[dregorapatches\]: Mixin config loaded: io\.github\.tidefox\.dregorapatches\.mixin\.levelup2' "Mixin loaded the Level Up! 2 late config"
require 'mixins\.dregorapatches\.lycanites\.json' "FermiumBooter saw the Lycanites late config"
require '\[dregorapatches\]: Mixin config loaded: io\.github\.tidefox\.dregorapatches\.mixin\.lycanites' "Mixin loaded the Lycanites late config"
require '\[dregorapatches\]: Dregora Patches .* ready' "dregorapatches started"
require '\[dregorapatches\]: /dpatch mixins:' "/dpatch mixins ran"
require '\[dregorapatches\]: Config reloaded' "/dpatch reload ran"
require 'Logging the next 1 deaths caused by a player' "/dpatch debug kill ran"
require 'No trick shots yet' "/trickshots read the (empty) records"
require 'Stopping server' "server stopped cleanly"
if [ "$mods_in" = 1 ]; then
  require 'patched: tick wrapper yes, NCTile yes, radiation scope yes, chunk guard yes' "/dpatch nc stats: all NC hooks patched"
  require 'Level Up! 2 furnace scan - fix on, patched yes' "/dpatch levelup2 stats: patched"
  require 'Lycanites pet recall - fix on, patched yes' "/dpatch lycanites stats: patched"
  require 'dim 0: .*port [0-9]+ frozen' "/dpatch nc stats: placed port is frozen"
  cfgs="vanilla nc levelup2 lycanites"
else
  require 'Mixin skipped \(target not installed\): .*MixinNCTile' "NC mixin skipped without NuclearCraft"
  cfgs="vanilla"
fi
# Every mixin listed in the configs must have applied (mod configs only when the mod is present).
for cfg in $cfgs; do
  for m in $(unzip -p "$DP" "mixins.dregorapatches.$cfg.json" | python3 -c 'import json,sys; c=json.load(sys.stdin); print(" ".join(c.get("mixins",[])+c.get("server",[])))'); do
    require "\[dregorapatches\]: Mixin applied: io\.github\.tidefox\.dregorapatches\.mixin\.$cfg\.$m " "mixin $cfg.$m applied"
  done
done

# Errors: mixin failures, ERROR/FATAL log lines, any exception text. Known-harmless lines are allow-listed.
# Known-harmless blocks (ci/log-block-allowlist.txt): drop the header line and the stack trace under it.
FILTERED=$(mktemp)
awk -v listf="$HERE/log-block-allowlist.txt" '
  BEGIN { while ((getline l < listf) > 0) if (l !~ /^#/ && l != "") pats[n++] = l }
  {
    if (skip && ($0 ~ /^[[:space:]]+(at |\.\.\. )/ || $0 ~ /^Caused by: / || $0 ~ /^[a-zA-Z_$][a-zA-Z0-9_$.]*(Exception|Error)(: |$)/)) next
    skip = 0
    for (i = 0; i < n; i++) if ($0 ~ pats[i]) { skip = 1; next }
    print
  }' "$ALL" > "$FILTERED"
BAD=$(grep -nE 'Mixin(Apply|Transformer)Error|InvalidMixinException|InvalidInjectionException|InvalidAccessorException|Mixin apply.*failed|/(ERROR|FATAL)\]|Exception|^\s+at [a-zA-Z_$]' "$FILTERED" \
      | grep -vEf "$HERE/log-allowlist.txt" || true)
if [ -n "$BAD" ]; then
  echo "::error::problems found in server log:"
  echo "$BAD" | head -80
  status=1
else
  echo "ok:   no mixin errors or exceptions in log"
fi
# FermiumBooter 1.4.x mirrors mixin problems into logs/fermiumbooter.log
if [ -f logs/fermiumbooter.log ] && grep -qE 'Mixin Error' logs/fermiumbooter.log; then
  echo "::error::logs/fermiumbooter.log reports mixin errors:"; head -60 logs/fermiumbooter.log; status=1
fi
[ -d crash-reports ] && { echo "::error::crash report written"; cat crash-reports/* | head -80; status=1; }

cp console.log "console-$1-$2.log"
return $status
}

# FML constructs coremods in jar-file order. Boot once with FermiumBooter sorting before
# our jar (the live server's file name) and once after it, so both enqueue paths run.
overall=0
if [ "${#EXTRA[@]}" -gt 0 ]; then
  boots=("FermiumBooter-1.4.1.jar with" "fermiumbooter.jar with" "FermiumBooter-1.4.1.jar without")
else
  boots=("FermiumBooter-1.4.1.jar without" "fermiumbooter.jar without")
fi
for boot in "${boots[@]}"; do
  set -- $boot
  echo "================ boot with mods/$1, $2 patched mods ================"
  run_once "$1" "$2" || overall=1
done
exit $overall
