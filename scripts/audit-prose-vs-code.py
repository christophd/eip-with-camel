#!/usr/bin/env python3
"""Surface numbers a chapter's prose asserts that its code blocks do not contain.

The 1000x circuit-breaker error survived every automated check because both
halves were individually valid: `.waitDurationInOpenState(10000)` compiled, and
"for 10 seconds" read fine. Only holding them side by side shows the mismatch.

This cannot decide correctness -- it narrows 43 chapters of prose down to the
sentences worth reading against the code. Every hit needs a human look.

    ./scripts/audit-prose-vs-code.py              # all chapters
    ./scripts/audit-prose-vs-code.py 02 18        # only matching
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "_docs"
FENCE = re.compile(r"^```(\w*)\n(.*?)^```", re.M | re.S)

# Numbers in prose that make a factual claim about behaviour.
CLAIM = re.compile(
    r"\b(\d[\d,]*(?:\.\d+)?)\s*"
    r"(seconds?|secs?|ms|milliseconds?|minutes?|mins?|hours?|"
    r"partitions?|consumers?|threads?|retries|retry|attempts?|"
    r"messages?|records?|times|percent|%)\b",
    re.I,
)
# Words that mean the number is illustrative, not a claim about this code.
HEDGE = re.compile(r"\b(for example|e\.g\.|say|suppose|imagine|might|could|typically|often|usually)\b", re.I)


FRONT_MATTER = re.compile(r"\A---\n.*?\n---\n", re.S)


def split_prose_and_code(text):
    # The front matter's `duration:` is a reading estimate, not a claim about code.
    text = FRONT_MATTER.sub("", text)
    code, prose_parts, last = [], [], 0
    for m in FENCE.finditer(text):
        prose_parts.append(text[last:m.start()])
        code.append(m.group(2))
        last = m.end()
    prose_parts.append(text[last:])
    return "\n".join(prose_parts), "\n".join(code)


def normalise(n):
    return n.replace(",", "").rstrip("0").rstrip(".") if "." in n else n.replace(",", "")


def main():
    filters = sys.argv[1:]
    total = 0
    for doc in sorted(DOCS.glob("*.md")):
        if filters and not any(f in doc.name for f in filters):
            continue
        text = doc.read_text(encoding="utf-8", errors="replace")
        prose, code = split_prose_and_code(text)
        if not code.strip():
            continue

        # Every numeric literal appearing anywhere in this chapter's code.
        code_nums = set()
        for tok in re.findall(r"\b\d[\d_]*(?:\.\d+)?\b", code):
            code_nums.add(normalise(tok.replace("_", "")))
            # A duration in ms is often written in prose as seconds.
            try:
                v = float(tok.replace("_", ""))
                if v >= 1000 and v % 1000 == 0:
                    code_nums.add(normalise(str(int(v // 1000))))
                code_nums.add(normalise(str(int(v * 1000))) if v < 100 else "")
            except ValueError:
                pass
        code_nums.discard("")

        hits = []
        for line in prose.split("\n"):
            if HEDGE.search(line):
                continue
            for m in CLAIM.finditer(line):
                num = normalise(m.group(1))
                if num in code_nums or num in {"0", "1", "2"}:
                    continue
                hits.append((m.group(0), line.strip()[:150]))

        if hits:
            total += len(hits)
            print(f"\n{doc.name}  ({len(hits)})")
            seen = set()
            for claim, line in hits:
                if line in seen:
                    continue
                seen.add(line)
                print(f"    [{claim}]  {line}")

    print(f"\n{total} prose number(s) with no matching value in the chapter's code.")
    print("Each needs reading -- many will be legitimate (rates, sizes, background facts).")


if __name__ == "__main__":
    main()
