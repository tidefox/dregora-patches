#!/usr/bin/env python3
"""Summarize a spark .sparkprofile into a small text report (no dependencies).

Usage:  python3 spark_summary.py <file.sparkprofile> [min_pct=0.3] [max_depth=40] > summary.txt

Reads the raw protobuf wire format directly (spark_sampler.proto field numbers),
so it needs only the Python 3 standard library. Accepts gzip-compressed input too.
"""
import collections
import gzip
import struct
import sys


# ---------------------------------------------------------------- wire format
def varint(b, i):
    r = s = 0
    while True:
        c = b[i]
        i += 1
        r |= (c & 0x7F) << s
        if c < 0x80:
            return r, i
        s += 7


def fields(b, start=0, end=None):
    """Yield (field_number, wire_type, value). value is int, float-bytes or memoryview."""
    i, end = start, len(b) if end is None else end
    while i < end:
        key, i = varint(b, i)
        fn, wt = key >> 3, key & 7
        if wt == 0:
            v, i = varint(b, i)
        elif wt == 1:
            v, i = b[i:i + 8], i + 8
        elif wt == 2:
            n, i = varint(b, i)
            v, i = b[i:i + n], i + n
        elif wt == 5:
            v, i = b[i:i + 4], i + 4
        else:
            raise ValueError("unsupported wire type %d" % wt)
        yield fn, wt, v


def doubles(wt, v, out):
    if wt == 1:
        out.append(struct.unpack("<d", v)[0])
    else:  # packed
        out.extend(struct.unpack("<%dd" % (len(v) // 8), v))


def ints(wt, v, out):
    if wt == 0:
        out.append(v)
    else:  # packed
        j = 0
        while j < len(v):
            x, j = varint(v, j)
            out.append(x)


# ---------------------------------------------------------------- messages
class Node(object):
    __slots__ = ("cls", "meth", "line", "total", "refs")

    def label(self):
        return "%s.%s" % (self.cls, self.meth)


def parse_stack_node(b):
    n = Node()
    n.cls = n.meth = ""
    n.line = 0
    times, n.refs = [], []
    for fn, wt, v in fields(b):
        if fn == 3:
            n.cls = bytes(v).decode("utf-8", "replace")
        elif fn == 4:
            n.meth = bytes(v).decode("utf-8", "replace")
        elif fn == 6:
            n.line = v
        elif fn == 8:
            doubles(wt, v, times)
        elif fn == 9:
            ints(wt, v, n.refs)
    n.total = sum(times)
    return n


def parse_thread(b):
    name, nodes, times, refs = "", [], [], []
    for fn, wt, v in fields(b):
        if fn == 1:
            name = bytes(v).decode("utf-8", "replace")
        elif fn == 3:
            nodes.append(parse_stack_node(v))
        elif fn == 4:
            doubles(wt, v, times)
        elif fn == 5:
            ints(wt, v, refs)
    return name, nodes, sum(times), refs, times


def parse_metadata(b):
    md = {}
    for fn, wt, v in fields(b):
        if fn in (2, 3, 11, 12):
            md[{2: "start", 3: "interval_us", 11: "end", 12: "ticks"}[fn]] = v
        elif fn == 5:
            for f2, _, v2 in fields(v):
                if f2 == 1:
                    md["aggregator"] = "TICKED" if v2 == 1 else "SIMPLE"
                elif f2 == 3:
                    md["tick_threshold_us"] = v2
                elif f2 == 4:
                    md["included_ticks"] = v2
        elif fn == 16:
            md["engine"] = "ASYNC" if v == 1 else "JAVA"
    return md


# ---------------------------------------------------------------- report
MC_PREFIXES = ("net.minecraft.", "net.minecraftforge.", "java.", "javax.", "sun.", "jdk.",
               "it.unimi.", "com.google.", "native.", "io.netty.")
CHUNK_LOAD = ("net.minecraft.world.gen.ChunkProviderServer", "loadChunk")
UPDATE_ENTITIES = ("net.minecraft.world.World", "func_72939_s")  # World.updateEntities


def main():
    path = sys.argv[1]
    min_pct = float(sys.argv[2]) if len(sys.argv) > 2 else 0.3
    max_depth = int(sys.argv[3]) if len(sys.argv) > 3 else 40

    raw = open(path, "rb").read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    buf = memoryview(raw)

    md, threads, windows = {}, [], []
    for fn, wt, v in fields(buf):
        if fn == 1:
            md = parse_metadata(v)
        elif fn == 2:
            threads.append(parse_thread(v))
        elif fn == 6:
            ints(wt, v, windows)

    out = sys.stdout.write
    dur = (md.get("end", 0) - md.get("start", 0)) / 1000.0
    out("== metadata\n%s\nduration_s=%.1f\n\n" % (md, dur))
    out("== threads\n")
    for name, nodes, total, _, _ in sorted(threads, key=lambda t: -t[2]):
        out("%12.0f ms  %s (%d nodes)\n" % (total, name, len(nodes)))

    srv = [t for t in threads if "Server thread" in t[0]]
    name, N, T, roots, per_window = srv[0] if srv else max(threads, key=lambda t: t[2])
    out("\nusing thread: %s  total=%.0f ms\n" % (name, T))
    if windows and len(per_window) == len(windows):
        out("per time window (minute index: ms): %s\n" % list(zip(windows, [round(x) for x in per_window])))
    pct = lambda n: 100.0 * n.total / T if T else 0.0
    kids = lambda n: sorted((N[i] for i in n.refs), key=lambda c: -c.total)

    # 1. call tree
    out("\n== call tree (>= %.2f%%, depth <= %d)\n" % (min_pct, max_depth))

    def tree(n, d):
        if pct(n) < min_pct or d > max_depth:
            return
        out("%s%6.2f%% %8.0fms %s\n" % ("  " * d, pct(n), n.total, n.label()))
        for c in kids(n):
            tree(c, d + 1)

    for r in sorted((N[i] for i in roots), key=lambda c: -c.total):
        tree(r, 0)

    # 2. tile entities / entities directly under World.updateEntities, by class
    ue = collections.Counter()
    # 3. who calls ChunkProviderServer.loadChunk (first non-vanilla frame + 2 above)
    loads = collections.Counter()
    disk = collections.Counter()
    # 4. self-ish time by mod package (first 2 package segments of first non-vanilla frame)
    bymod = collections.Counter()

    def walk(n, path, under_ue):
        lab = n.label()
        if (n.cls, n.meth) == UPDATE_ENTITIES:
            for c in kids(n):
                ue[c.label()] += c.total
        if (n.cls, n.meth) == CHUNK_LOAD:
            mods = [p for p in path if not p.startswith(MC_PREFIXES)]
            key = " <- ".join(reversed(mods[-3:])) if mods else "(vanilla) " + " <- ".join(reversed(path[-4:]))
            loads[key] += n.total

            def has_disk(x):
                return "AnvilChunkLoader" in x.cls or any(has_disk(N[i]) for i in x.refs)
            if has_disk(n):
                disk[key] += sum(N[i].total for i in n.refs if has_disk(N[i]))
            return
        child_total = sum(N[i].total for i in n.refs)
        self_t = n.total - child_total
        if self_t > 0:
            mods = [p for p in path + [lab] if not p.startswith(MC_PREFIXES)]
            pk = ".".join(mods[-1].split(".")[:2]) if mods else "(vanilla/jdk)"
            bymod[pk] += self_t
        for i in n.refs:
            walk(N[i], path + [lab], under_ue)

    sys.setrecursionlimit(100000)
    for i in roots:
        walk(N[i], [], False)

    out("\n== World.updateEntities children (tile entities etc.)\n")
    for k, v in ue.most_common(40):
        out("%6.2f%% %8.0fms %s\n" % (100.0 * v / T, v, k))
    out("\n== ChunkProviderServer.loadChunk callers (total / of which disk read)\n")
    for k, v in loads.most_common(40):
        out("%8.0fms %8.0fms  %s\n" % (v, disk.get(k, 0), k))
    out("\n== time by package of innermost non-vanilla frame (self time)\n")
    for k, v in bymod.most_common(30):
        out("%6.2f%% %8.0fms %s\n" % (100.0 * v / T, v, k))


if __name__ == "__main__":
    main()
