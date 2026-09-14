#!/usr/bin/env python3
"""Find identifiers a chapter's prose names that appear nowhere in its code.

Complements audit-prose-vs-code.py, which handles numeric claims. This catches
the other half: prose that refers to a header, option, endpoint or route that
the chapter's code -- and the example it points at -- never mention. That is how
`camel.component.redis-lettuce.host` and a dispatcher keyed on `OrderPlaced`
survived, both describing code that did not exist.

    ./scripts/audit-prose-identifiers.py           # all chapters
    ./scripts/audit-prose-identifiers.py 12 17     # only matching

Hits need reading. Prose legitimately names Camel APIs, Kafka settings and
third-party classes that this project never calls.
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "_docs"
FENCE = re.compile(r"^```(\w*)\n(.*?)^```", re.M | re.S)
FRONT = re.compile(r"\A---\n.*?\n---\n", re.S)
EXAMPLE_LINK = re.compile(r"examples/([\w-]+)[/)\s]")
BACKTICK = re.compile(r"`([^`\n]{3,70})`")

# Identifier shapes worth checking. Prose naming one of these is asserting the
# code contains it.
SHAPES = [
    re.compile(r"^[a-z][\w-]*:[\w./${}\[\]-]+$"),            # endpoint URI  kafka:topic
    re.compile(r"^Camel[A-Z]\w+$"),                            # Camel header  CamelFileName
    re.compile(r"^camel\.[\w.-]+$"),                           # camel config key
    re.compile(r"^quarkus\.[\w.-]+$"),                         # quarkus config key
    re.compile(r"^[a-z][a-zA-Z0-9]*\([^)]*\)$"),               # DSL call      timeout(5000)
]
# Shapes that match but are background knowledge, not claims about this code.
IGNORE = re.compile(
    r"^(https?|file|classpath|mailto|jdbc|bean|ref|class|java|sql|xml|json|yaml|e\.g|i\.e):"
    r"|^camel\.(component|main|springboot|rest|servlet|threadpool|health|metrics)\.[\w.-]+$"
    r"|^quarkus\.(http|log|native|package|datasource|kubernetes|container-image)\."
)


def main():
    filters = sys.argv[1:]
    total = 0
    for doc in sorted(DOCS.glob("*.md")):
        if filters and not any(f in doc.name for f in filters):
            continue
        raw = doc.read_text(encoding="utf-8", errors="replace")
        text = FRONT.sub("", raw)

        code_parts, prose_parts, last = [], [], 0
        for m in FENCE.finditer(text):
            prose_parts.append(text[last:m.start()])
            code_parts.append(m.group(2))
            last = m.end()
        prose_parts.append(text[last:])
        code = "\n".join(code_parts)
        prose = "\n".join(prose_parts)
        if not code.strip():
            continue

        # Widen the haystack with the example the chapter points at.
        m = EXAMPLE_LINK.search(raw)
        if m:
            ex = ROOT / "examples" / m.group(1)
            if ex.is_dir():
                for f in ex.rglob("*"):
                    if f.suffix in (".java", ".yaml", ".properties", ".xml") and "target" not in f.parts:
                        code += "\n" + f.read_text(encoding="utf-8", errors="replace")

        hits = []
        for tok in dict.fromkeys(BACKTICK.findall(prose)):
            if IGNORE.search(tok):
                continue
            if not any(s.match(tok) for s in SHAPES):
                continue
            # A DSL call matches if its method name appears; the args may differ.
            probe = tok.split("(")[0] if tok.endswith(")") else tok
            if probe in code:
                continue
            hits.append(tok)

        if hits:
            total += len(hits)
            print(f"\n{doc.name}  ({len(hits)})")
            for h in hits:
                print(f"    {h}")

    print(f"\n{total} prose identifier(s) absent from the chapter's code and its example.")


if __name__ == "__main__":
    main()
