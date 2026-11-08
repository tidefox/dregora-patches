#!/usr/bin/env bash
# Downloads FermiumBooter from CurseMaven and checks that it is the expected version.
# usage: ci/fetch-fermium.sh <file-id> <expected-version> <out.jar>
set -euo pipefail
PROJECT=968474
id="$1"; want="$2"; out="$3"
url="https://www.cursemaven.com/curse/maven/fermiumbooter-${PROJECT}/${id}/fermiumbooter-${PROJECT}-${id}.jar"
echo "Downloading $url"
curl -fsSL --retry 4 --retry-delay 5 -o "$out" "$url"
# Capture the listing first: "unzip | grep -q" fails under pipefail when grep exits early (SIGPIPE).
listing=$(unzip -l "$out")
grep -q 'fermiumbooter/FermiumRegistryAPI.class' <<<"$listing" || { echo "::error::$out is not FermiumBooter"; exit 1; }
got=$(unzip -p "$out" mcmod.info 2>/dev/null | tr -d '\r' | grep -o '"version"[^,]*' | head -1 | sed -E 's/.*"version" *: *"([^"]*)".*/\1/' || true)
echo "FermiumBooter jar version: ${got:-unknown}"
if [ "$got" != "$want" ]; then
  echo "::error::Expected FermiumBooter $want, CurseForge file $id is '${got:-unknown}'"
  exit 1
fi
