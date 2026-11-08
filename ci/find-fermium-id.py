#!/usr/bin/env python3
"""Print the CurseForge file id of a FermiumBooter version (default 1.4.1).

Used only when gradle.properties has no pinned fermium_file_id. Uses the public
CurseForge website API (no key needed)."""
import json
import re
import sys
import urllib.request

PROJECT = 968474
want = sys.argv[1] if len(sys.argv) > 1 else "1.4.1"
pat = re.compile(r"(?<![0-9.])" + re.escape(want) + r"(?![0-9])")

found = None
for page in range(0, 6):
    url = ("https://www.curseforge.com/api/v1/mods/%d/files?pageIndex=%d&pageSize=50"
           "&sort=dateCreated&sortDescending=true&removeAlphas=false" % (PROJECT, page))
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (dregora-patches CI)", "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = json.load(r).get("data", [])
    if not data:
        break
    for f in data:
        name = "%s | %s" % (f.get("fileName"), f.get("displayName"))
        print("  file %s: %s" % (f.get("id"), name), file=sys.stderr)
        if found is None and pat.search(name):
            found = f.get("id")
    if found:
        break

if not found:
    sys.exit("FermiumBooter %s not found on CurseForge" % want)
print(found)
