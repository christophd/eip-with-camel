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
| **1** | Site materials | `iteration/full-walkthrough-01` | ☑ **complete** — 1 bug found and fixed |
| **2** | PPTX decks | `iteration/full-walkthrough-02` | ☑ **complete** — 2 issues found and fixed |
| **3** | Every example, run not compiled | `iteration/full-walkthrough-03` | ☑ **complete** — 2 bugs, 3 CI gaps |
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
| 1.3 | Codetabs — label/content correspondence and label vocabulary (HTML checks; behaviour spot-checked by the user) | ☑ pass |
| 1.4 | Diagrams — integrity, pairing, alt/caption, and accounting across chapters, READMEs and decks | ☑ pass |
| 1.5 | Navigation — part indexes, prev/next, breadcrumbs, homepage card grid | ☑ pass |
| 1.6 | Internal + external links resolve | ☑ pass |

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

**1.3 — clean.** 132 tab panels checked for label/content correspondence: every
"Quarkus" panel looks like Quarkus, every "Spring Boot" panel like Spring Boot,
no panel carries the other runtime's markers. This is the failure that matters,
because the convention is positional — the Nth block must match the Nth label —
and nothing enforces it.

The label vocabulary is also consistent, which is load-bearing: `codetabs.js`
syncs selection *by label text*, so a single "SpringBoot" or "Spring boot" would
silently break persistence for that group. Exactly three labels are in use
(`Quarkus` 61, `Spring Boot` 61, `YAML DSL` 10) across two combinations, and
both assets ship and are referenced.

**1.4 — clean, and nothing is orphaned.** 67 SVGs, each with a paired
`.excalidraw` source, all valid XML with dimensions, all shipped to `_site`,
and every `excalidraw.html` include carries both alt text and a caption.

Accounting for all 67, which took three passes because they are used in three
different places:

| Where | Count |
|---|---|
| Chapter `excalidraw.html` includes | 47 |
| Example READMEs (the `ex-*` architecture diagrams) | 17 |
| Deck PNGs under `presentations/src/png/` | 51 |
| **Used somewhere** | **67 — no orphans** |

A first pass that only scanned `_docs/` reported 20 orphans. It was wrong: 17
are embedded in example READMEs, and the remaining three — `11-resequencer`,
`12-type-conversion`, `17-purger-proxy` — are used by the slide decks.

> **Do not delete unreferenced assets.** 22 of the 51 deck PNGs are not
> currently placed on a slide. They stay: they are cheap, and they are the
> obvious raw material for future slides. The same goes for any diagram that
> looks unused — check chapters, example READMEs *and* the decks before
> concluding anything.

**1.5 — clean.** All 44 chapter pages carry breadcrumb and prev/next markup,
and the prev/next chain is unbroken end to end: walking chapters in `order`,
every page links to both of its neighbours, 0 through 43. The homepage links
all 10 part pages, every part index lists its chapters, and of 105 internal
hrefs across chapters, parts, examples and the homepage, none is broken.

**1.6 — clean.** 199 external URLs across chapters, example READMEs and the
root documents. Twenty do not resolve and all twenty are correct:

- eighteen are deliberately fictional or container-internal hosts inside code
  examples — `*.example.com`, `payment-1:8080`, `credit-bureau:8080`,
  `otel-collector:4317`, and `http://d/v1.41/version`, which is the dummy host
  in a `curl --unix-socket` call
- `my-resource.openai.azure.com` is a placeholder endpoint
- `strimzi.io/charts/` is the Helm repository URL; a browser GET 404s but
  `index.yaml` returns 200, which is what Helm fetches

A future run should skip `*.example.com`, single-label hosts and anything
inside a fenced code block, or it will re-report these every time.

### Part 1 outcome

One real defect, found and fixed: the Liquid placeholder loss. Everything else
verified clean. Structural checks were done against the built HTML rather than
a browser, by agreement — **worth a spot check from a human:** that tabs visibly
switch and persist across pages, and that a few diagrams read well at page
width.

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
| 2.1 | Decks rebuild from source (`presentations/src/build.sh`) | ☑ pass — byte-identical |
| 2.2 | EIP 101 (98 slides) — content accurate against current chapters | ☑ pass |
| 2.3 | EIP 201 (140 slides) — same, with attention to code slides and versions | ☑ pass |
| 2.4 | Embedded diagrams current — 51 PNGs against 67 diagrams on disk | ☑ **2 issues fixed** |
| 2.5 | Legacy deck — keep as-is, regenerate, or retire | ☑ keep as-is |

**Findings:**

**2.1 — pass, and better than hoped.** Both decks rebuild and the slide XML is
**byte-identical** to what was committed, so the shipped decks are genuinely in
sync with their source and the build is deterministic. `pptxgenjs` is a local
dependency in `presentations/src/package.json`, so the README's instruction to
install it globally is unnecessary — `npm install` in that directory is enough.

**2.2 / 2.3 — no factual errors.** Extracted all text from both decks (39,737
and 82,635 characters) and tested it against every claim the chapters were
corrected on:

| Checked | Result |
|---|---|
| Camel version strings | none anywhere — nothing to go stale |
| `toD` / dynamic-router security guidance | neither deck gives any, so none is superseded |
| at-least-once / manual commit | conceptual only, not tied to the mechanism that changed in 4.22 |
| `stopOnException` | one use, on a **recipientList**, which is unchanged |
| `camel-opentelemetry` v1 deprecation | not mentioned |
| saga / compensation | conceptual, no version-specific claim |

The gaps are **omissions, not errors**: `allowedSchemes` and the Splitter's new
partial-failure options appear in neither deck. That is defensible — these decks
teach the 65 patterns, not the Camel release notes — so it is recorded as a
choice rather than a defect.

EIP 101's "complete catalog" slide gives per-category counts that sum correctly
to 65 and abbreviates the longest categories with "more"; five patterns are
never named in full, which is a slide-space decision, not a miscount.

**2.4 — two real problems, both fixed.**

*One diagram was stale.* `01-order-flow` was redrawn on 2026-07-12, the day
after the PNGs were generated, and the deck had been showing the old render
ever since. Caught by comparing each SVG's viewBox aspect ratio against its
PNG's: 50 of 51 matched within 8%, and that one was 29% off. Regenerated; EIP
101's embedded media changes as a result, EIP 201 does not use it.

*The conversion script could not reproduce its own output.* The SVGs carry a
viewBox but no width, so LibreOffice picks its own size — the committed PNGs
were uniformly 1920 wide, while a re-run produced 1426 to 1843. Anyone
regenerating would have silently downgraded every slide image. `-resize` now
pins the width (override with `DIAGRAM_WIDTH`), the script takes diagram names
as arguments so single diagrams can be refreshed without touching the rest, and
it handles ImageMagick 7's rename of `convert`.

Sixteen diagrams added since the last conversion run had no PNG at all. They do
now, at the same 1920. All 67 PNGs are uniform, and the 50 untouched ones were
deliberately left alone rather than churned.

**2.5 — keep the legacy deck.** `Apache Camel and Enterprise Integration
(Final).pptx` has no source under `src/`, is referenced only by the README that
explains exactly that, and is not a build target. It stays, per the standing
instruction not to discard material that may be wanted later.

---

## Part 3 — Every example, run not compiled

The long pole. Budget several hours; it is mostly sequential and needs the
stack up.

| # | Checkpoint | State |
|---|---|---|
| 3.1 | Compile sweep — 61 Maven projects (`build-all-examples.sh`) | ☑ pass 61/61 |
| 3.2 | Boot sweep — every built artifact starts (`verify-all-runtime.sh`) | ☑ **1 failure found and fixed** |
| 3.3 | Route activity — each example actually processes messages, not just boots | ☑ pass, with 2 gated |
| 3.4 | YAML DSL — 11 directories via the Camel CLI | ☑ pass 11/11 |
| 3.5 | Operational scripts — share groups, diagnostics, Connect offsets | ☑ pass 3/3 |
| 3.6 | Verification footers reconciled with what was observed | ☑ pass — 1 corrected |
| 3.7 | CI workflows reviewed — all three, including the externally contributed `tests.yml` | ☑ **3 gaps closed** |

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

**3.1 — 61/61 compile.**

**3.2 — 58 of 59 booted; the one failure was the exact trap this script exists
for, in an example added two days ago.** `23-quarkus-dev` failed with
`No language could be found for: bean`. Its `.log()` uses Simple map accessors
(`${body[order_id]}`), which resolve through the bean language, and the pom
never declared `camel-quarkus-bean` — while the Citrus test dependencies were
supplying `camel-bean` on the *test* classpath. So `mvn test` passed, `mvn
quarkus:dev` ran, and only the artefact you would actually ship failed to
start.

Its verification footer claimed more than had been checked. Both the chapter
footer and the example README now record that the packaged application boots,
and say why that check was added.

This is the third time this specific trap has appeared in three days. It is
worth treating "does the packaged artifact boot" as a separate, mandatory gate
rather than something implied by a green test run.

**3.7 — three real gaps in CI, all closed.**

| Workflow | Was missing | Why it mattered |
|---|---|---|
| `examples.yml` | `19-dsl-comparison`, `23-quarkus-dev`, `26-feature-flags` | three Maven examples never compiled by CI |
| `tests.yml` (quarkus) | `23-quarkus-dev`, `37-testing-strategies` | `37` has had tests since July and had **never** run in CI |
| `tests.yml` (spring-boot) | `37-testing-strategies` | same |

Both additions were checked against a CI-like environment first — stack fully
down, Testcontainers on the Podman socket — and both pass: `23-quarkus-dev`
2/2, `37-testing-strategies` 4/4.

`19-dsl-comparison` is deliberately **not** in the yaml-dsl test job. That job
runs `citrus run <example>/yaml-dsl/test`, and the example has no `test/`
directory because it exists to be diffed across runtimes, not tested. The
yaml-dsl matrix covers exactly the ten directories that do have one.

All three matrices are now complete against what is on disk: 32/32 Maven
projects, 22/22 Quarkus test suites, 21/21 Spring Boot, 10/10 YAML DSL.

**3.4 — 11/11 YAML DSL examples start** under `camel run`, with route counts
from 1 to 13.

**3.5 — all three operational scripts work.** Share groups splits nine orders
3/3/3 across workers and demonstrates ACCEPT, RELEASE and REJECT correctly;
diagnostics walks its seven steps; Connect offsets takes the topic from 10
records to 20 after the `PATCH` rewind and resets cleanly.

**Also found during 3.5: `setup-stack.sh` could not recover a corrupt Pulsar
volume.** Bringing the stack up after a `podman-compose down` died on "Bookie
handle is not available". The script already knew about BookKeeper corruption
and wiped the volume when it found the container in an exited state — but that
check runs *before* startup, and `down` removes the container while leaving the
volume, so it saw nothing. Pulsar now gets one automatic recovery attempt after
the health wait, `wait_healthy` gained a nonfatal mode and stops waiting out
the timeout on an already-exited container, and it prints logs when it gives up
rather than failing silently. Verified by corrupting the ledger deliberately
and re-running: FAILED, wiped, retried, healthy.

**3.3 — message flow, scoped to where it adds information.** The boot sweep
already proves every artifact starts and its routes come up, and the Citrus
suites already assert message flow for 43 of the projects. So rather than
re-driving everything, this checked the examples where *nothing* has ever
asserted that a message moves — the eight with runtimes but no test suite:

| Example | Evidence |
|---|---|
| `19-dsl-comparison` | driven end to end on all three runtimes, 2026-09-15 |
| `26-feature-flags` | driven end to end on both runtimes, 2026-09-15 |
| `32-kafka-consumer-tuning` | **driven now** — routes processed messages |
| `33-kafka-producer-tuning` | **driven now** — routes processed messages |
| `loan-broker`, `bond-trading` | Part 4 |
| `38-kubernetes-deploy` | gated: needs a cluster, and its footer says so |
| `42-ai-mcp` | gated: needs Ollama |

An attempt to infer activity from the boot-sweep logs was abandoned — the
detection kept over- or under-matching because Quarkus and Spring Boot format
route logging differently, and a log-shaped heuristic is not evidence. Driving
the examples is.

**3.6 — footers hold up.** Three chapters claim "verified" on the strength of
compilation: 02 and 03 are conceptual and say so explicitly, and 38 states that
the application runs but that the Kubernetes manifests "are not exercised here;
that needs a cluster." All three describe exactly what was and was not done.
The only overstated footer found in this part was `23-quarkus-dev`, corrected
under 3.2.

> **A note for whoever runs this next.** Two attempts at scripting the YAML DSL
> sweep killed the harness shell. The first used `pkill -f "camel run"`, which
> matched the runner's own command line — the exact thing the working notes
> below warn about. The second used `kill -TERM -- -$pid` on a child that was
> not a process-group leader, which signalled the caller's group instead. Let
> `timeout` own process lifecycle and never kill by pattern or by group.

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
