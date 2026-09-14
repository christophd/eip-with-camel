# Phase 1 Plan — Camel 4.22 / Quarkus 3.39.3 / Spring Boot 4.1.1 upgrade

**Status:** Phase 1 complete, plan APPROVED by user 2026-09-12. Executing.
**Date:** 2026-09-12

## User gate decisions (2026-09-12 — do not re-open)

1. **All four open issues (#12, #13, #14, #15) land in this iteration.**
2. **Full local retest against the real Podman stack.** All 28 chapter verification
   footers flip to `unverified` during the upgrade and are only re-marked `verified`
   for examples that actually run, with real dates.
**Supersedes the research sections of** `upgrade-camel-4.22-HANDOFF.md` (recon there is still valid).

---

## Research results — corrections to the handoff

### Version targets (verified against `repo1.maven.org` maven-metadata.xml, not search.maven.org)

> `search.maven.org`'s solrsearch index is stale — it reports Spring Boot topping out at
> 3.5.3 and Citrus at 4.6.0. Do not use it. Use `repo1.maven.org/.../maven-metadata.xml`.

| Component | Current | Handoff said | **Verified target** |
|---|---|---|---|
| Apache Camel | 4.20.0 | 4.22.0 | **4.22.0** ✓ (LTS; 4.22.1 is documented in the upgrade guide but *not yet released*) |
| Quarkus platform | 3.37.0 | 3.39.1 | **3.39.3** — 3.39.1 is superseded |
| Camel Quarkus | — | — | 3.39.0 (standalone BOM latest) |
| Spring Boot | 4.0.7 | 4.1.1 | **4.1.1** ✓ (4.2.0-M1 is a milestone — skip) |
| Citrus | docs 4.4.0 / PRs 5.0.0 | reconcile | **5.0.1** |
| Java | 25 | 25 | 25 — no pom change |
| CI JDK | 21 | 25 | 25 — confirmed cause of the red build |

**Verified:** `io.quarkus.platform:quarkus-camel-bom:3.39.3` pins
`org.apache.camel:camel-core-engine:4.22.0`. The platform/Camel mapping holds.

### Breaking-change triage — two of the handoff's flagged risks are non-issues here

The handoff flagged "dynamic URI allow-list for `toD`/`enrich`" and "JEP-290
deserialization filter" as the top runtime-breakage risks. Both were guesses. Checked
against the real 4.22 upgrade guide and this repo:

**1. `toD`/`enrich` property placeholders — REAL change, but this repo is unaffected.**
The actual change: `toD` and `enrich` no longer resolve `{{…}}` placeholders on the
*per-message evaluated recipient*. Placeholders written directly in the route's endpoint
URI (the static template) still resolve. Every `{{…}}` in this repo's `toD`/`pollEnrich`
is in the static template:
- `bond-trading/DemoDataRoute.java:84` — `.toD("kafka:${header.TargetTopic}?brokers={{kafka.brokers}}")` — static template, **fine**
- `14-consumer-patterns/PollingConsumerRoute.java:20` — `pollEnrich` with `{{kafka.brokers}}` — static template, and `pollEnrich` is explicitly unchanged in this release, **fine**

No route in this repo builds a `{{…}}` token from message content. **No action.**

**2. JEP-290 deserialization filter — REAL change, but this repo is unaffected.**
Scoped to `camel-spring-redis` (default serializer now installs an `ObjectInputFilter`)
and `CamelObjectInputStream` (HTTP paths, which already applied it). This repo's Redis
chapter uses `quarkus-redis-client` / `spring-boot-starter-data-redis`, **not**
`camel-spring-redis`. **No action.**

**3. Resilience4j duration API — REAL, and it does hit this repo.**
Getters/setters/builders for `waitDurationInOpenState`, `slowCallDurationThreshold`,
`timeoutDuration`, `bulkheadMaxWaitDuration` changed `Integer` → `String`. Additionally
`waitDurationInOpenState` and `slowCallDurationThreshold` changed **seconds → milliseconds**.
Blast radius is small — the examples' `application.properties` only set
`camel.resilience4j.sliding-window-size` and `failure-rate-threshold` (both unaffected).
Only two doc snippets use the changed API:
- `_docs/02-integration-styles.md:207` — `.waitDurationInOpenState(10000)`
- `_docs/18-testing-management.md:283` — `.waitDurationInOpenState(30)`

Both must become quoted duration expressions (`.waitDurationInOpenState("30s")`).
Note `_docs/02`'s `10000` was already suspect under the old seconds semantics
(≈2.8 hours open) — fix the value, not just the type.

**4. camel-jbang — Preview → Stable, plus real feature drift.** Affects appendices
39 (CLI), 40 (TUI), 42 (AI/MCP):
- Camel CLI, TUI, **and MCP Server all promoted Preview → Stable**
- Config file renamed `camel-jbang-user.properties` → `camel-cli.properties`
  (auto-migrated on first run). *Repo does not currently reference the old name — grep clean.*
- TUI: switchable light/dark themes, `--theme`, F4 toggle, Settings in the F2 menu,
  `camel.tui.*` keys
- `camel cmd route-diagram` / `route-topology` now accept route **source files**
  (no running integration needed) — directly relevant to ch. 40 and to `scripts/generate_diagram.py` workflows
- `camel run|dev|debug --openapi-ui`
- CLI no longer hardcodes Quarkus 3.33.1.1; it resolves the version

**5. `camel-ai-tool` gained `structuredContent`** — this is the substance behind open
issue #13.

**6. Other 4.22 breaks — none apply.** Weaviate v6, IBM MQ 10, Azure `CredentialType`,
camel-tika, camel-reactive-executor-tomcat, camel-grok, camel-dynamic-router,
remote-file jail checks: no such dependency in this repo. Verified component inventory:
bean, controlbus, direct, jackson, jsonpath, kafka, langchain4j-chat, log, management,
micrometer, mock, opentelemetry, platform-http, pulsar, rest, servlet, sql, timer, jta,
microprofile-fault-tolerance/health, resilience4j, test-spring-junit5.

**7. Spring AI 2.0** (shipped in Camel 4.22) targets Spring Boot 4.1 / Spring Framework 7
— consistent with the 4.1.1 target. No conflict.

---

## Resolutions to the five open plan questions

### Q1 — How do the 21 untested PRs get verified before landing?

**Fix CI first as step 0.** The red `Build Examples` workflow is the missing regression
net, and it is a two-line fix (JDK 21 → 25). Landing 21 PRs onto a repo with no working
CI reproduces the exact problem that made them untrustworthy.

Sequence: fix CI → confirm green on `main` → merge PRs onto an integration branch in one
batch → full local build → push and let CI judge the batch.

Rationale for batch-over-per-PR: the PRs are disjoint by chapter directory; the only
shared file is `.gitignore` (PR #2 adds `.camel-jbang/`, `.citrus-jbang/`). Per-PR CI
would be 21 fork-approval round-trips for near-zero extra signal.

Also expand the CI matrix — it currently covers 17 of 31 examples, omitting 20, 21, 22,
24, 25, 27, 32, 33, 37, 38, 39, 40, 41, 42.

### Q2 — Where does image pinning sit relative to the PR merge?

**After.** The PRs add ~40 new `src/test/resources/_infra/compose.yaml` files that also
use `:latest`. Pinning before the merge means doing it twice. One pass, after.

### Q3 — Aggregator pom vs scripted edits across 57 poms?

**Scripted edits; no aggregator.** An aggregator would invalidate the documented
per-example `cd examples/<name>/<runtime> && mvn …` commands in CLAUDE.md, in
GETTING-STARTED.md, and in every chapter's run instructions — a large documentation
change in service of a one-off build convenience.

Critical scripting detail already verified: `quarkus.platform.version` (28×) and
`camel.version` (27×) are **properties**, but `spring-boot-starter-parent` 4.0.7 is a
**hardcoded literal inside `<parent>`** in all 27 Spring Boot poms. A property-only bump
silently misses every one. The script needs both a property pass and a `<parent>` pass,
plus a post-check that greps for surviving `4.20.0` / `3.37.0` / `4.0.7`.

### Q4 — Citrus 4.4.0 (docs) vs 5.0.0 (PRs)?

Standardize on **5.0.1**. The PRs bring 5.0.0; bump to 5.0.1 during the merge pass and
rewrite appendix 41's three 4.4.0 references (`_docs/41-appendix-citrus-testing.md`
lines ~45, 591, 597, 801, 802, 810).

### Q5 — Mechanical vs semantic split

**Mechanical** (scriptable, verifiable by grep, parallel-safe):
- M1 CI JDK 21 → 25, expand example matrix
- M2 `quarkus.platform.version` 3.37.0 → 3.39.3 (28 poms, property)
- M3 `camel.version` 4.20.0 → 4.22.0 (27 poms, property)
- M4 `spring-boot-starter-parent` 4.0.7 → 4.1.1 (27 poms, **literal in `<parent>`**)
- M5 Citrus → 5.0.1
- M6 Pin all `:latest` container images in `examples/_infra/compose.yaml`,
  `compose.lgtm.yaml`, and the ~40 PR-added test compose files
- M7 Chapter footer version strings: `Camel 4.20.0` → `4.22.0`,
  `Quarkus 3.37.0` → `3.39.3`, `Spring Boot 4.0.7` → `4.1.1` (28 chapters)
- M8 Flip every chapter's verification status to **unverified** — the existing
  "verified … on Podman (2026-07-11)" footers become false the moment versions move,
  and stay false until workstream 3 re-runs them

**Semantic** (judgment, serial, needs review):
- S1 Resilience4j duration snippets (`_docs/02:207`, `_docs/18:283`) — type *and* unit
- S2 Appendices 39/40/42: Preview → Stable, TUI theming/settings, `route-diagram`/
  `route-topology` source-file mode, `--openapi-ui`, `camel-cli.properties`
- S3 Appendix 41 rewrite to Citrus 5.0.1
- S4 Spring Boot 4.0 → 4.1 fallout in the 27 Spring Boot examples
- S5 The four open issues (see gate question)

---

## Content audit — added 2026-09-14 after the user asked whether the plan covered it

It did not, and that was a real gap. The plan's semantic work (S1–S4) only
covered what the 4.22 upgrade guide happened to flag. Everything else found so
far was found *incidentally* while chasing those items:

| Found | How | In the plan? |
|---|---|---|
| `redis-lettuce` component does not exist in any Camel version | chasing the Redis JEP-290 question | no |
| ch 39's two YAML files had `steps` as a sibling of `from` — schema-invalid | chasing `redis-lettuce` | no |
| ch 40's two YAML files had the same bug | running a check prompted by ch 39 | no |
| ch 39/40 `rest:`/`post:` block shape was invalid | same | no |
| `waitDurationInOpenState(10000)` meant 10,000 seconds while the prose said 10 | Resilience4j triage | partly |
| `/components/4.20.x/` doc links already 404 across 12 chapters | version sweep | no |
| malformed footer markdown in ch 02 | footer rewrite | no |

That hit rate says the remaining, unlooked-at content almost certainly holds
more of the same. Incidental discovery is not a strategy, so the following two
steps are now explicit and gate the re-verification of any chapter footer.

### A1 — DSL validation sweep (mechanical, scriptable)

Covers every runnable artifact, not just the ones a chapter happens to mention:

1. Validate every YAML DSL route against the Camel 4.22 schema.
2. Resolve every endpoint URI scheme in both YAML and Java against the 4.22
   component catalog. This is the check that would have caught `redis-lettuce`
   on day one.
3. Compile every Java snippet embedded in a chapter against 4.22, not just the
   example sources. Chapters and examples are separate code.
4. Resolve every external link in the docs. The Camel doc links are
   version-scoped and silently rot on every upgrade.

### A2 — Per-chapter content audit (semantic, one chapter at a time)

For each of the 43 chapters:

1. **Chapter/example parity** — code shown in the chapter must match the
   runnable example it points at. The 21 merged Citrus PRs modified
   `src/main/java` (`DemoDataGenerator`, `PulsarDemoDataGenerator`,
   `RedisChannelRoute`, `RedisDemoDataGenerator`), so drift is expected here,
   not hypothetical.
2. **Prose/code agreement** — narrative claims about values, timings and
   behaviour must match what the code does. This is the class of bug that hid
   the 1000x circuit-breaker error in plain sight.
3. **Config key existence** — properties referenced in prose and snippets must
   be real options on the component (`camel.component.redis-lettuce.host` was
   not).
4. **4.22 currency** — support levels, CLI/TUI flags and config file names
   reflect 4.22, not 4.20.

A chapter's footer may only return to `verified` after A1 and A2 pass for it
*and* its example actually runs in Step 6.

---

## Execution order

```
Step 0  Fix red CI (M1)                      → confirm green on main
Step 1  Merge 21 Citrus PRs onto integration → resolve .gitignore once, local build
Step 2  Mechanical version bumps (M2–M5)     → grep post-check
Step 3  Image pinning (M6)
Step 4  Semantic doc/code work (S1–S4)
Step 5  Footers + verification status (M7–M8)
Step 6  DSL validation sweep (A1)            → build the checker, fix what it finds
Step 7  Per-chapter content audit (A2)       → 43 chapters
Step 8  Full local retest (workstream 3)     → re-verify footers with real dates
Step 9  Open issues #12, #13, #14, #15
```

Every step commits. Work happens on an iteration branch, never checkpointed onto `main`.
No `Co-authored-by` trailers.

---

## Known risks

- **Spring Boot 4.0 → 4.1 is a minor bump across 27 standalone poms with no aggregator.**
  Failures will surface 27 times independently. Budget for it.
- **The 21 PRs modify `src/main/java`**, not only tests (`DemoDataGenerator`,
  `PulsarDemoDataGenerator`, `RedisChannelRoute`, `RedisDemoDataGenerator`). They change
  production example code that chapters quote. Chapter text may drift from the code.
- **`redis-lettuce:` appears as a Camel endpoint URI** in the Redis example. That is not a
  standard Camel component scheme — flag for verification in Step 6; the example may never
  have run.
- **CI covers 17 of 31 examples.** Green CI is weaker evidence than it looks until M1
  expands the matrix.
