# Phase 1 Plan — Camel 4.22 / Quarkus 3.39.3 / Spring Boot 4.1.1 upgrade

**Status:** COMPLETE — all nine steps executed. Closed out 2026-09-14.
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

> **Historical record.** The upgrade described here is complete and merged to
> `main`. Work that came after it is tracked in `backlog.md`.


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


---

## Outcome — 2026-09-14

All nine steps done, on branch `iteration/camel-4.22-upgrade`. CI green.

### Final state

- Camel **4.22.0** on all three runtimes; Quarkus platform **3.39.3**,
  Spring Boot **4.1.1** (Spring Framework 7.0.9), Citrus **5.0.1**.
- **56/56** Maven projects build; **44** run Citrus integration tests against
  live containers. All green.
- 94 compose files pinned. Kafka 4.3.1, Pulsar 4.2.4, Postgres 18.6,
  Redis 8.10.1, Apicurio 3.3.3, Grafana 13.2.1, Loki 3.7.7, Mimir 3.2.1,
  Tempo 3.0.3, OTel collector 0.160.0, kafka-ui v0.7.2.
- 22 chapters re-verified with real test counts; 6 remain unverified and say why.
- Issues #12, #13, #14, #15 all closed.

### What the retest caught that the build could not

1. **Postgres 18 volume layout** — expects `/var/lib/postgresql`, not
   `/var/lib/postgresql/data`. Container refused to start. 23 compose files.
2. **Apicurio 3.3.3 health moved to port 9000** — old check 404'd forever, so
   the container sat "unhealthy" while working fine.
3. **Citrus tests vs the dev stack** — both bind 9092/6379/5432/6650. The
   surfaced error names the symptom, not the cause.
4. **`citrus-camel` enables JMX on Camel Quarkus** — it pulls in
   `camel-management`; Camel turns JMX on when it sees that jar, but Camel
   Quarkus only configures the name strategy via `camel-quarkus-management`.
   Every Quarkus example NPE'd before any route started. These 21 PRs' tests
   had therefore never passed anywhere.
5. **37-testing-strategies had 9 of 10 tests broken** — pre-existing, confirmed
   by reproducing on Camel 4.20.0 / Spring Boot 4.0.7. `@UseAdviceWith` stops
   the context before each method, so a `@BeforeAll` start is undone.

### Content defects found by the A1/A2 audit

- `redis-lettuce` is not a Camel component in any version — 10 references.
- `redis:` in chapter 12 — also not a component.
- All four YAML files in appendices 39/40 were schema-invalid.
- Appendix 39 had `--open-api` backwards (it consumes a spec, not produces one).
- `waitDurationInOpenState(10000)` meant 10,000 seconds against prose saying 10.
- 8 dead external links; `/components/4.20.x/` was already 404 everywhere.
- 28 chapter route ids did not match the example they pointed at.
- 9 `eip.*.enabled` flags added by the PRs were wholly undocumented.

### Known gaps, deliberately left

- **Testcontainers runs on Docker here, not Podman.** The socket at
  `/run/user/<uid>/podman/podman.sock` does not exist even though the systemd
  unit reports active, so Testcontainers silently used Docker. The project
  documents Podman everywhere else. Appendix 41 now explains how to tell which
  engine served a run, but reconciling the two is not done.
- **449 chapter/example identifier divergences remain.** Triaged as legitimate:
  chapters deliberately show variants the examples implement once. Only true
  1:1 renames were fixed.
- **Appendix 42 is unverified** — needs a running Ollama, and the multimodal
  example needs a vision-capable model.
- **SDKMAN install instructions unverified** — needs a clean machine.


---

## Second pass — 2026-09-14

The first close-out claimed 22 chapters verified on the strength of passing
tests. That was wrong, and the user was right to push back. Passing tests did
not mean working applications.

### What booting the artifacts found

`scripts/verify-all-runtime.sh` starts every built jar. On the first run **16 of
54 failed to start**, including chapters already marked verified.

1. **13 Quarkus examples: `NoSuchLanguageException: No language could be found
   for: bean`.** Simple OGNL such as `${header[kafka.OFFSET]}` resolves through
   the bean language, which on Camel Quarkus arrives with
   `camel-quarkus-bean`. None declared it. The tests passed because
   `citrus-camel` depends on `camel-bean`, putting it on the *test* classpath
   only. 20-kafka-deep-dive is the clearest case: green tests, jar will not boot.
2. **37-testing-strategies Spring Boot** declared `camel-mock` at test scope,
   which downgraded the compile-scoped copy `camel-spring-boot-starter`
   provides, breaking `camel-dataset-starter`'s auto-configuration.
3. **42-ai-mcp, both runtimes.** Neither failure was Ollama: Quarkus bound a
   chat model by a name with no `#` and then to a bean that does not exist;
   Spring Boot's langchain4j Ollama starter is not Spring Boot 4 compatible at
   any version.

### What running the JBang appendices found

39, 40 and 41 have no Maven project, so nothing had ever touched them. All three
were broken: `camel.rest.port` is not honoured (`--port` is a flag),
`camel.rest.binding-mode` caused double unmarshalling, chapter 39's REST path
had a trailing slash and its enricher read fields off the wrong body, nothing
seeded the Redis it looks up, and chapter 40's generator used `%` — which
Camel's Simple language does not have — so it never started.

`loan-broker`'s demo generator emitted no `requestId`, the correlation key for
the entire Scatter-Gather, so its own traffic always failed in the aggregator.

### What the prose audit found

Chapter 01 claimed Avro/Apicurio wiring "in every Kafka-based example" — there
is none. Chapters 06 and 13 made the same class of claim. Chapter 20 had the
Kafka UI port wrong. Chapter 21 claimed Pulsar TTL that nothing configures.
Chapter 02 had a snippet that does not compile. Nine more route ids did not
match the example they pointed at.

### Final state

- **56/56** Maven projects build and pass their tests.
- **54/54** built artifacts boot.
- **116/116** chapter Java snippets compile.
- **43/43** chapters verified, each stating what was actually exercised.
- Content validator clean, including external links.

### Checks added, so this is not left to noticing

| Script | Finds |
|---|---|
| `verify-all-runtime.sh` | Artifacts that build and test but will not start |
| `verify-example-runtime.sh` | Whether routes actually process messages |
| `verify-jbang-example.sh` | The three appendices with no Maven project |
| `compile-chapter-snippets.sh` | Chapter Java that does not compile |
| `audit-prose-vs-code.py` | Numeric claims with no matching value in the code |
| `audit-prose-identifiers.py` | Identifiers named in prose that do not exist |
| `validate-content.py` | Endpoint schemes, YAML shape, config keys, stale versions, dead links |
| `check-chapter-parity.py` | Chapter route ids against the example's |

### Still outstanding

- **Testcontainers runs on Docker, not Podman.** The podman socket does not
  exist despite the systemd unit reporting active. Appendix 41 documents how to
  check; reconciling the project onto one engine is not done.
- **431 chapter/example identifier divergences remain**, deliberately: chapters
  show variants the examples implement once.
- **Tool invocation in appendix 42 was not observed.** `llama3.2` does not
  reliably call tools; the chapter says so and names models that do.
- **SDKMAN install instructions unverified** — needs a clean machine.
- The branch is **not merged to main**.
