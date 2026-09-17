---
title: "Full Walkthrough"
description: "The gating quality pass over the whole project — site, decks, every example, and the case studies. Run verification, not compile checks."
render_with_liquid: false
---

The final quality gate: does all of this actually work together. Explicitly
**not** a compile sweep — the standard is that the thing runs and does what the
chapter says it does.

**Started:** 2026-09-17
**One branch per part**, merged to `main` as each part closes:
`iteration/full-walkthrough-01` … `-04`.

---

## Status

| Part | Scope | Branch | State |
|---|---|---|---|
| **1** | Site materials | `iteration/full-walkthrough-01` | ☐ in progress |
| **2** | PPTX decks | `iteration/full-walkthrough-02` | ☐ not started |
| **3** | Every example, run not compiled | `iteration/full-walkthrough-03` | ☐ not started |
| **4** | Case studies end to end | `iteration/full-walkthrough-04` | ☐ not started |

Scope as of the start: **44 chapters, 37 examples, 61 Maven projects, 11 YAML
DSL directories, 3 operational scripts, 3 decks, 67 diagrams.**

---

## Part 1 — Site materials

Every chapter page, codetabs rendering, links, diagrams, front matter.

Much of the *structural* checking was done in the post-upgrade close-out
(see `backlog.md` item 8) and is re-run here rather than trusted: the repo has
changed since.

| # | Checkpoint | State |
|---|---|---|
| 1.1 | Structural re-check — front matter, diagram includes, `{% link %}` targets, Jekyll build clean | ☑ pass |
| 1.2 | Every chapter page renders — all 44 reachable, no Liquid leakage, no broken layout | ☑ **bug found and fixed** |
| 1.3 | Codetabs actually behave — tabs switch, selection syncs across a page, persists across pages | ☐ |
| 1.4 | Diagrams render — all 67 present, referenced, and not visually broken | ☐ |
| 1.5 | Navigation — part indexes, prev/next, breadcrumbs, homepage card grid | ☐ |
| 1.6 | Internal + external links resolve | ☐ |

**Findings:**

**1.1 — clean.** 44 chapters, 10 parts, orders 0–43 with no gaps or duplicates,
every chapter has the five required front matter keys and a verification
footer, all diagram includes and `{% link %}` targets resolve, and a
from-scratch Jekyll build (cache deleted) is warning-free.

**1.2 — 34 Camel property placeholders were being eaten by Liquid across nine
chapters.** The published site was showing

```
from("kafka:eip.orders.placed?brokers=&groupId=inventory-service")
```

where the source says `?brokers={{kafka.brokers}}`. Liquid parses `{{...}}` as
an output tag *inside fenced code blocks too*, evaluates `kafka.brokers` as an
undefined variable, and substitutes empty string. Every affected snippet was
uncopyable, and the damage was invisible in the markdown.

Fixed by wrapping the offending fences in `{% raw %}` / `{% endraw %}`, which is
the convention chapter 19 already used. Affected: 04, 09, 12, 32, 33, 34, 38,
39, 40. Chapter 19 was already safe.

Two things worth remembering. First, presence-checking is not enough: chapters
04, 09 and 12 looked fine because each had *one* correctly guarded mention in
prose, which masked two broken ones in code. Only counting occurrences source
against rendered exposed it. Second, the naive fix nests `raw` inside an
existing `raw` region, and Liquid treats the inner tag as literal text so the
first `endraw` closes the outer block and the second is orphaned — the build
fails with "Unknown tag endraw" in a *different* chapter than the one at fault.

Re-verified after the fix: 34/34 placeholders present, all 61 codetabs markers
still have their blocks, and no raw tags leak into the HTML.

---

## Part 2 — PPTX decks

Both generated decks checked against current content, plus a decision on the
legacy one.

The decks are the most likely thing in the repo to be quietly stale: nothing
regenerates them when a chapter changes, and the chapters have since taken on
Camel 4.22, `allowedSchemes`, the Splitter options, three new appendix
examples and a new security appendix.

| # | Checkpoint | State |
|---|---|---|
| 2.1 | Decks rebuild from source (`presentations/src/build.sh`) | ☐ |
| 2.2 | EIP 101 (98 slides) — content accurate against current chapters | ☐ |
| 2.3 | EIP 201 (140 slides) — same, with attention to code slides and versions | ☐ |
| 2.4 | Embedded diagrams current — 51 PNGs against 67 diagrams on disk | ☐ |
| 2.5 | Legacy deck — keep as-is, regenerate, or retire | ☐ |

**Findings:**

---

## Part 3 — Every example, run not compiled

The long pole. Budget several hours; it is mostly sequential and needs the
stack up.

| # | Checkpoint | State |
|---|---|---|
| 3.1 | Compile sweep — 61 Maven projects (`build-all-examples.sh`) | ☐ |
| 3.2 | Boot sweep — every built artifact starts (`verify-all-runtime.sh`) | ☐ |
| 3.3 | Route activity — each example actually processes messages, not just boots | ☐ |
| 3.4 | YAML DSL — 11 directories via the Camel CLI | ☐ |
| 3.5 | Operational scripts — share groups, diagnostics, Connect offsets | ☐ |
| 3.6 | Verification footers reconciled with what was observed | ☐ |
| 3.7 | CI workflows reviewed — all three, including the externally contributed `tests.yml` | ☐ |

**On 3.7.** There are three workflows and we have only ever looked at one.
`tests.yml` was contributed in August by Christoph Deppisch (the Citrus
maintainer) and runs the Citrus suites in a matrix — but its matrix was written
against the example set as it stood then, and six examples have been added
since. Check what each workflow actually covers, whether the matrices are still
complete, whether they currently pass, and whether `examples.yml` and
`tests.yml` overlap or contradict each other.

**Why boot separately from compile:** `verify-all-runtime.sh` exists because
passing tests did not imply a working application. The Citrus test dependencies
put `camel-bean` on the *test* classpath, so routes using Simple OGNL started
fine under test and failed at runtime in an app that never declared it. Tests
exercise the test classpath; only booting the built artifact exercises the
runtime one.

**Findings:**

---

## Part 4 — Case studies end to end

The two largest examples, and the only ones claiming a specific pattern count.

| # | Checkpoint | State |
|---|---|---|
| 4.1 | loan-broker — both runtimes, end to end | ☐ |
| 4.2 | bond-trading — both runtimes, end to end | ☐ |
| 4.3 | Pattern claims verified — 13 for loan-broker, 16 for bond-trading | ☐ |
| 4.4 | Chapters 28 and 29 reconciled with observed behaviour | ☐ |

**Findings:**

---

## Working notes

- The tests and the dev stack cannot both be up: both bind 9092, 6379, 5432,
  6650. Bring the stack down before anything that uses Testcontainers.
- Testcontainers needs `DOCKER_HOST` pointed at the Podman socket locally.
  `build-all-examples.sh` does it; bare `mvn verify` does not.
- `.github/workflows/tests.yml` was contributed externally in August and runs
  the Citrus suites on GitHub runners, which have Docker natively — so it needs
  no Podman handling. Reviewed under checkpoint 3.7.
- Never `pkill -f <pattern>` with the pattern in your own command line.
- Rootless podman containers are host java processes; a blanket kill over java
  PIDs takes down Kafka, Pulsar and Apicurio.
