#!/usr/bin/env bash
# Downloads a mod jar from CurseMaven and checks its sha1 (the exact jar the server runs).
# usage: ci/fetch-mod.sh <slug> <project-id> <file-id> <sha1> <out.jar>
set -euo pipefail
slug="$1"; project="$2"; id="$3"; want="$4"; out="$5"
url="https://www.cursemaven.com/curse/maven/${slug}-${project}/${id}/${slug}-${project}-${id}.jar"
echo "Downloading $url"
curl -fsSL --retry 4 --retry-delay 5 -o "$out" "$url"
got=$(sha1sum "$out" | cut -d' ' -f1)
if [ "$got" != "$want" ]; then
  echo "::error::$slug file $id: sha1 $got, expected $want"
  exit 1
fi
echo "$slug $id ok (sha1 $got)"
