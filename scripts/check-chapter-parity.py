#!/usr/bin/env python3
"""Compare the code a chapter shows against the runnable example it points at.

Chapters and examples are separate code. When an example changes -- as all 21
merged Citrus PRs changed src/main/java -- nothing makes the chapter follow, so
the tutorial quietly starts describing code that no longer exists.

This compares the two on signals that should agree: route ids, direct:
endpoints, and Kafka/Pulsar topic names. It deliberately does not diff text,
because chapters legitimately simplify and elide.

    ./scripts/check-chapter-parity.py            # summary table
    ./scripts/check-chapter-parity.py 22 18      # detail for matching chapters
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "_docs"

# Chapters point at their example either through a "Runnable example" link or,
# in the appendices, through a bare examples/<name>/ path in a cd command.
EXAMPLE_LINK = re.compile(r"examples/([\w-]+)[/)\s]")
FENCE = re.compile(r"^```(\w*)\n(.*?)^```", re.M | re.S)

ROUTE_ID = re.compile(r'\.routeId\(\s*"([^"]+)"|^\s*id:\s*["\']?([\w.-]+)', re.M)
DIRECT = re.compile(r'"direct:([\w.-]+)"')
TOPIC = re.compile(r'"(?:kafka|pulsar)://?([\w.${}\[\]-]+)')


def extract(text):
    ids, directs, topics = set(), set(), set()
    for m in ROUTE_ID.finditer(text):
        ids.add(m.group(1) or m.group(2))
    directs.update(DIRECT.findall(text))
    for t in TOPIC.findall(text):
        # Ignore fully dynamic destinations.
        if "${" not in t:
            topics.add(t.split("?")[0])
    return ids, directs, topics


def chapter_code(path):
    text = path.read_text(encoding="utf-8", errors="replace")
    blocks = [body for lang, body in FENCE.findall(text) if lang in ("java", "yaml", "")]
    return "\n".join(blocks)


def example_code(example_dir):
    parts = []
    for p in example_dir.rglob("*"):
        if "target" in p.parts or "/test/" in str(p):
            continue
        if p.suffix in (".java", ".yaml") and "src/test" not in str(p):
            parts.append(p.read_text(encoding="utf-8", errors="replace"))
    return "\n".join(parts)


def main():
    filters = sys.argv[1:]
    rows = []
    for doc in sorted(DOCS.glob("*.md")):
        text = doc.read_text(encoding="utf-8", errors="replace")
        m = EXAMPLE_LINK.search(text)
        if not m:
            continue
        ex = ROOT / "examples" / m.group(1)
        if not ex.is_dir():
            rows.append((doc.name, m.group(1), "MISSING example dir", None))
            continue

        c_ids, c_dir, c_top = extract(chapter_code(doc))
        e_ids, e_dir, e_top = extract(example_code(ex))

        only_example = (e_ids - c_ids, e_dir - c_dir, e_top - c_top)
        only_chapter = (c_ids - e_ids, c_dir - e_dir, c_top - e_top)
        rows.append((doc.name, m.group(1), only_example, only_chapter))

    detail = bool(filters)
    print(f"{'chapter':<42} {'example':<26} {'in example only':>16} {'in chapter only':>16}")
    print("-" * 104)
    total = 0
    for name, ex, only_e, only_c in rows:
        if isinstance(only_e, str):
            print(f"{name:<42} {ex:<26} {only_e}")
            continue
        ne = sum(len(s) for s in only_e)
        nc = sum(len(s) for s in only_c)
        total += ne + nc
        flag = "  <-- drift" if ne or nc else ""
        print(f"{name:<42} {ex:<26} {ne:>16} {nc:>16}{flag}")

        if detail and any(f in name for f in filters) and (ne or nc):
            for label, sets in (("only in example", only_e), ("only in chapter", only_c)):
                ids, dirs, tops = sets
                if ids:
                    print(f"      {label} - route ids : {sorted(ids)}")
                if dirs:
                    print(f"      {label} - direct:    : {sorted(dirs)}")
                if tops:
                    print(f"      {label} - topics     : {sorted(tops)}")
    print("-" * 104)
    print(f"{total} total divergent identifier(s) across {len(rows)} chapters")


if __name__ == "__main__":
    main()
