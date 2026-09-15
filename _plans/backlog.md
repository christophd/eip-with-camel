---
title: "Backlog"
description: "The single live tracker for remaining work on the tutorial — post-upgrade cleanup, appendix examples, and the close-out document review."
render_with_liquid: false
---

This is the canonical list of what is left to do. `iteration-plan.md` is the
historical record of what shipped; this file is what is still open. Work the
sections in order and commit each item separately.

**Last reviewed:** 2026-09-15
**Branch:** `iteration/post-upgrade-cleanup`, pushed, clean tree

**Resume here:** items 1, 2, 3, 4 and 6 are closed. Item 5 is 3 of 6 done —
next is deciding the shape of the three Kafka operational examples (34, 35,
36), which may be scripts rather than Maven projects. Item 7a (stale chapters)
is done and 7b is done except the optional security appendix. Item 8, the
close-out review, is last and not started.

Known stale and waiting for item 8: `README.md` still says 41 chapters and 31
examples; both are now 43 and 35.

---

## Where the project stands

Feature-complete. 43 chapters covering all 65 EIP patterns, 35 example projects
(three added 2026-09-15), 67 diagrams, 3 presentation decks.

The Camel 4.22 upgrade is merged to `main`: Camel **4.22.0** on all three
runtimes, Quarkus platform **3.39.3**, Spring Boot **4.1.1** (Spring 7.0.9),
Citrus **5.0.1**, JDK 25. 56/56 Maven projects build, 44 run integration tests
against live containers, 54/54 boot, 43/43 chapters verified. Full write-up in
`upgrade-camel-4.22-PLAN.md`.

Everything below is what that pass did *not* close.

---

## 1. LGTM stack  ☑ done 2026-09-15

**Stack side: done 2026-09-15.** `./scripts/setup-stack.sh --lgtm` now brings up
all five services and all three signals were observed landing. Starting it for
the first time surfaced four defects, all fixed:

- Tempo would not start at all — `metrics_generator.traces_storage` is not a
  field in Tempo 3.0.3, so it exited on a config parse error
- Loki, Mimir and the collector ship **distroless** images, so their
  `wget`-based healthchecks could never run; they sat permanently unhealthy and
  Grafana and the collector, both waiting on `condition: service_healthy`, were
  never created. Healthchecks removed where impossible; readiness now polled
  from the host by `wait_ready()` in `setup-stack.sh`
- The collector exported traces to `tempo:3200`, the query port, not the OTLP
  receiver on 4318 — a silent no-op
- Only traces ever left the application. The example carried the Prometheus
  registry alone, so its 93 `camel_*` meters were exposed for a scrape nobody
  performed, and logs went to the console. The Quarkus
  `micrometer-opentelemetry` bridge now puts metrics and logs on the same OTLP
  exporter as traces

Verified with five orders through `eip.orders.placed`: traces queryable in
Tempo, `camel_exchanges_total{routeId=...}` in Mimir, route log lines in Loki.

**Spring Boot variant: done.** It was worse than the Quarkus one — it emitted
*nothing*. The `otel.exporter.otlp.*` properties had no exporter and no
autoconfiguration behind them, only the SDK that Camel pulls in transitively.
Three separate things were needed, each failing silently:

- the OpenTelemetry Spring Boot starter, for the exporter and Logback appender
- `OpenTelemetryGlobalConfig`, because Camel's tracer resolves its SDK from the
  Camel registry or `GlobalOpenTelemetry.get()`, and the starter publishes
  neither — so route spans vanished while the starter's own HTTP spans kept
  arriving, which made tracing look healthy. Confirmed against both
  `camel-opentelemetry` and `camel-opentelemetry2`
- `spring-boot-opentelemetry`, without which Spring Boot 4 skips
  `OtlpMetricsExportAutoConfiguration` on a missing-class condition while still
  accepting every `management.otlp.metrics.export.*` property.
  `/actuator/conditions` is what found it

**Chapter 27: rewritten.** Corrected the metric names (`camel_exchanges_total`,
`camel_route_policy_milliseconds_bucket`), the LogQL labels (`service_name`,
`bridge_name`), the ports table, and the Loki section, which taught
`quarkus.log.console.json=true` — JSON to stdout, shipped nowhere. Added a
"getting all three signals" section explaining why metrics and logs fail
quietly, the 4.21 span-shape changes from item 7a, the two metric-naming traps
(seconds vs milliseconds across the two paths; counters absent until they
count), and a copy-pasteable end-to-end check. Footer now records what was
observed and says plainly that the Citrus tests are not evidence for this page.

## 2. Presentation deck references Camel 3.0.0  ☑ not a defect, 2026-09-15

False positive from the original audit, which matched a bare `3.0.0`.

The string is on slide 10 of
`presentations/Apache Camel and Enterprise Integration (Final).pptx`, and it is
`openapi: 3.0.0` — the spec version in a sample OpenAPI contract next to a
sample WSDL. It has nothing to do with Camel and must not be "fixed".

Swept all three decks for version claims while confirming this. The only other
three-part numbers anywhere are a sample `version: "1.0.0"` in that same
contract and `127.0.0.1` on slide 21. `eip-101.js` and `eip-201.js` name no
Camel, Quarkus or Spring Boot version at all, so there is nothing in the decks
that goes stale on a version bump.

## 3. Stale handoff document  ☑ done

`_plans/upgrade-camel-4.22-HANDOFF.md` deleted — superseded by
`upgrade-camel-4.22-PLAN.md` and `upgrade-camel-4.22-RESUME.md`.

## 4. Chapter/example identifier divergences  ☑ done 2026-09-15

**The count was the wrong thing to chase, and it barely moved: 438 → 434.** That
is the correct outcome. Working through it chapter by chapter confirmed the
original triage — the overwhelming majority are chapters showing three named
variants of a pattern the example implements once
(`idempotent-receiver-jdbc`/`-memory`/`-redis` against one
`idempotent-receiver`, `load-balancer-round-robin`/`-failover`/`-sticky` against
one `load-balancer-demo`, `polling-consumer-cron`/`-file` against one
`polling-consumer`). Renaming those would make the tutorial worse, so they stay.

What the pass *did* find is a different and much worse problem that the
identifier count does not measure: **four chapters teach a pattern their example
does not implement at all**, while pointing the reader at that example.

| Ch | Pattern with no code behind it |
|---|---|
| 10 | **Process Manager** — the chapter shows a full `saga()` with compensation; the example has no saga anywhere |
| 08 | **Request-Reply** and **Return Address** — no responder in the example |
| 12 | **Envelope Wrapper** — three of four patterns are implemented, not this one |
| 13 | **Canonical Data Model** — not missing, but it is the `domain-model` module rather than a route, which nothing said |

Each now states, in the runnable-example callout, which patterns are in the code
and which are prose only, and why. Verified the other way too: chapters 04, 06,
11 and 18 have complete pattern coverage, so their divergence really is just
naming.

Also aligned two identifiers that were plain renames with no teaching purpose
behind them: ch 20's `exactly-once-pipeline` is the example's
`transactional-pipeline`, and ch 07's `direct:send-payment-command` is
`direct:send-command`.

**Do not treat the remaining 434 as a defect count.** Use
`check-chapter-parity.py` to find chapters worth *looking* at; the question is
always whether a reader following along is misled, and usually the answer is no.

## 4b. Original framing (kept for context)

Lowest value, largest surface. `./scripts/check-chapter-parity.py` reports the
current count (432 across 33 chapters as of 2026-09-15).

These were triaged during the upgrade and deliberately left: chapters routinely
show three variants of a pattern the example implements once (three
idempotent-receiver backing stores, two polling-consumer triggers, three
load-balancer strategies). Collapsing those would make the tutorial worse.
37 true 1:1 renames were already fixed.

**Before touching this, re-read that reasoning.** The useful work here is not
mass renaming — it is deciding, per chapter, whether the divergence misleads a
reader following along. Consider adding a short note to the chapters with the
largest gaps rather than changing identifiers. Current worst offenders by
chapter-only count: 37-testing-strategies (16), 27-observability (14),
25-quarkus-flow (10).

---

## 5. Runnable examples for appendix gaps  ◐ 3 of 6 done

**Done and verified 2026-09-15:**

- **26 — Feature Flags.** `examples/26-feature-flags/`, both runtimes, flagd in
  the base stack. Pin flagd **v0.16.3**: v0.13.1 cannot serve the Java provider
  0.14.2 event stream, and the failure is silent — every flag returns its
  default while curl against the RPC works fine
- **23 — Quarkus Dev Mode.** `examples/23-quarkus-dev/`, Quarkus-only by
  design. The one example meant to be edited while it runs, and the only one
  that leaves Dev Services on
- **19 — Runtime Comparison.** `examples/19-dsl-comparison/`, all three
  runtimes. The two Java classes differ by four lines; the README opens by
  telling you to diff them

**Still to build — 34, 35, 36.** All three are Kafka *operational* topics
rather than route-level ones, and the open question from the original triage
still stands: they may be better served by scripts and manifests than by Maven
projects. Decide per chapter, and if a chapter deliberately gets no Maven
module, say so in the chapter.

| Ch | Topic | Notes before starting |
|---|---|---|
| 34 | Kafka Share Groups (KIP-932) | **Check Camel support first.** The stack runs Kafka 4.3.1 so the broker side is there, but Camel's Kafka component may have no share-consumer support, in which case this is a CLI/`kafka-console-share-consumer` script, not a route |
| 35 | Kafka Diagnostics | Thread and heap dumps, JMX, flame graphs — almost certainly a script rather than a project |
| 36 | Kafka Connect Offsets | Strimzi CRDs, so it needs the minikube stack from ch 38 rather than the Podman one. Manifests plus a script |

**Two Quarkus gotchas that recurred across both new Quarkus examples** — expect
them again in any new one:

- A `simple` expression using a map accessor (`${body[amount]}`) resolves
  through the bean language, so the project needs `camel-quarkus-bean` or the
  route fails at startup with `No language could be found for: bean`
- `setHeader(String, Supplier)` needs the same extension

And on Spring Boot: the run-controller property is `camel.main.run-controller`.
`camel.springboot.main-run-controller` is accepted silently and does nothing, so
an app with no web starter exits seconds after its routes start. Two examples
had the wrong key.

## 5b. Original framing (kept for context)

Six appendix chapters have no example project behind them. Chapters 30
(Glossary) and 31 (Virtual Threads) are excluded by design — they do not need
one.

| Chapter | Appendix | Example to build | Notes |
|---|---|---|---|
| 19 | DSL Comparison | `examples/19-dsl-comparison/` | Same route in Java DSL on both runtimes plus YAML DSL — the chapter's whole point is the side-by-side |
| 23 | Quarkus Dev Mode | `examples/23-quarkus-dev/` | Live reload, dev services, continuous testing; Quarkus-only by nature |
| 26 | Feature Flags | `examples/26-feature-flags/` | flagd + OpenFeature was an original project decision that never got code |
| 34 | Kafka Share Groups | `examples/34-kafka-share-groups/` | KIP-932; check Camel/Kafka client support before committing to a design |
| 35 | Kafka Diagnostics | `examples/35-kafka-diagnostics/` | Thread/heap dumps, Strimzi additional volumes — may be script-and-manifest rather than a Maven project |
| 36 | Kafka Connect Offsets | `examples/36-kafka-connect-offsets/` | Strimzi CRDs for list/alter/reset; overlaps the minikube stack in ch 38 |

Follow the established shape: `quarkus/` + `spring-boot/` subdirectories, YAML
DSL where the pattern suits it, shipping domain throughout, Citrus tests, and a
verification footer that reflects an actual run. Some of these (34–36) are
operational rather than route-level and may be better served by scripts and
manifests than by a Camel project — decide per chapter, and say so in the
chapter if there is deliberately no Maven module.

---

## 6. Testcontainers and the container socket  ☑ done 2026-09-15

**The premise was wrong, and the earlier note in memory with it.** Testcontainers
does *not* require Docker here. The Podman socket exists at
`/run/user/<uid>/podman/podman.sock`, serves a Docker-compatible API at v1.44,
and Testcontainers works against it. Verified by running
`examples/07-message-types/quarkus` with `DOCKER_HOST` pointed at it: BUILD
SUCCESS, 3 tests, 0 failures. **Ryuk works too** — no need for
`TESTCONTAINERS_RYUK_DISABLED`, and disabling it leaks containers on
interrupted runs, so `build-all-examples.sh` no longer sets it.

What was actually true: a real Docker Engine is installed on this machine at
`/var/run/docker.sock`, and Testcontainers defaults to it, so the tests had been
running on a *different engine* than everything else — silently, because they
pass either way.

So the documentation says "point Testcontainers at Podman", not "install
Docker". Landed in chapter 00 (a new "Testcontainers and the container socket"
section, plus a pointer from "Why Podman over Docker?" so that section no longer
misleads), `README.md`, `GETTING-STARTED.md`, `CONTRIBUTING.md`, chapter 37,
chapter 41, and `examples/_infra/README.md`. All of them also state the port
contention with the dev stack.

Two bugs fixed in passing:

- `GETTING-STARTED.md` told readers to run every example with a loop over
  `examples/*/pom.xml`. Those poms moved into `quarkus/` and `spring-boot/`
  subdirectories in the multi-language work, so the loop had silently matched
  nothing for months. Replaced with `build-all-examples.sh`, including the
  ~2 hour cost of `--with-tests` and the filter argument
- `examples/_infra/README.md` documented the LGTM services without readiness
  checks and implied all of them have healthchecks. Now records which are
  distroless and why only Grafana has one

## 7. Camel 4.21 / 4.22 content gap  ◐ mostly done

**7a, stale chapters: done 2026-09-15.** All six corrected — ch 14
(`allowedSchemes`, with both example variants updated), ch 05/15/32 (the Kafka
manual-commit guarantee only became true in 4.22), ch 10 (saga coordinator),
ch 13 (JDBC aggregation repository), ch 39 (CLI install path, `self-update`,
`doctor`). Ch 11 gained the Dynamic Router cross-reference.

**7b, additions: done except one.** Landed the Splitter's `errorThreshold` /
`maxFailedRecords` / `group` / watermark options in ch 09 with runnable routes
on both runtimes, the zero-config observability CLI in ch 27 and 39, `--jfr`
and the runtime JFR events in ch 39, the tool-calling safety rails in ch 42,
the `spring.kafka.*` bridge in ch 20, and JMX percentiles in ch 17.

Deliberately skipped, with reasons: group-scoped variables (ch 08 has no
variables section to extend) and the canonical YAML DSL work (ch 19 compares
runtimes, not DSLs, despite its title).

**Still open — the optional security appendix.** "Secure out of the box" was
the headline theme across both releases and the site has no security chapter.
Write it **only as one argument** — that Camel's posture moved from "safe if
you configure it" to "safe by default" — covering JEP-290 deserialization
filters, Jackson polymorphic-type blocking, header filtering at transport
boundaries, dynamic URI allow-lists, Zip/Tar Slip prevention, credential
masking and `oauthProfile`. If it cannot be written that way, cut it; the
`allowedSchemes` work in ch 14 already carries the part that matters most.

## 7c. Research detail (kept for reference)

Researched 2026-09-15. The upgrade was mechanical — version bumps and fixing
what broke — so nobody looked at what 4.21 and 4.22 actually *shipped*. The
tooling appendices (39, 40, 42) already absorbed a lot of it incidentally. The
gap is in the core pattern chapters 02–18, which nobody revisited.

Sources: the [4.21](https://camel.apache.org/manual/camel-4x-upgrade-guide-4_21.html)
and [4.22](https://camel.apache.org/manual/camel-4x-upgrade-guide-4_22.html)
upgrade guides and the two what's-new blogs. Note 4.22 is **LTS**; supported
lines are now 4.18.x and 4.22.x.

### 7a. Stale — chapters teaching something 4.21/4.22 changed

Higher priority than the additions: these are wrong today.

| Ch | Problem | Effort |
|---|---|---|
| 14 | `_docs/14-consumer-patterns.md:274` teaches a hand-rolled allow-list before `toD()` as the answer to URI injection. 4.22 shipped `allowedSchemes` on `toD`/`enrich` as the framework answer. The manual check is still needed for the endpoint-value problem — reframe it as the second layer, do not delete it | Small + 1 line in the example |
| 27 | "What gets traced automatically" (line 75) is now factually wrong. 4.21 removed the redundant processor span wrapping endpoint spans (`EndpointSending`), added `disableCoreProcessors` to `camel-telemetry`, dropped ThreadLocal/Scope wrapping in `camel-opentelemetry2` and added `includePatterns`, and made `traceCustomIdOnly` filter at route level. A reader comparing the chapter to a real trace sees a different shape | Small |
| 05, 15, 32 | All three teach `allowManualCommit=true` and assert at-least-once. True only from 4.22: before it, the framework auto-committed every processed record even when the route never called `commit()`. The code is unchanged and correct — the *guarantee* is new. Good teaching moment | Small, one paragraph in 32, cross-ref from 05 |
| 10 | The Saga example's runtime behaviour changed — `InMemorySagaCoordinator` now returns the real finalization future, so the exchange waits for compensation and propagates failure instead of logging a warning. Better pedagogically, but undocumented. Re-run the example to confirm the log ordering in the prose still holds | Small–medium |
| 13 | Recommends `JdbcAggregationRepository` for production. 4.22 fixed a real correctness bug there (`INSERT … ON CONFLICT` was missing the `version` column), added schema-qualified table names, and made `remove()` throw on stale delete. 4.21 separately fixed `RedisAggregationRepository` to use per-key locks — relevant to ch 22 | Small |
| 39 | Installation/Upgrading teach `jbang app install camel@apache/camel`. 4.22 made the canonical path a web installer (`curl -fsSL https://camel.apache.org/install.sh \| sh`) and added `camel self-update` and a `camel doctor` that reports conflicting installs. Note `camel update` (OpenRewrite) is a *different* command the chapter already covers | Small |

Verified as **not** affected, so do not spend time re-checking: removed
components (stomp, aws-xray, guava-eventbus, grape, elytron, github), the newly
deprecated list, the Resilience4j duration-string change, ch 31's virtual-thread
property, the `toD`/`enrich` placeholder-expansion change, JMS `ObjectMessage`,
FTP path containment, and the 30+ component header-constant renames. None appear
in `_docs/`.

### 7b. Additions worth making

Ranked. The first four are the ones to actually do.

1. **Splitter error thresholds and chunking — ch 09, medium, highest value.**
   The only core EIP that gained real capability in this range. 4.22 added
   `errorThreshold` (fractional), `maxFailedRecords` (absolute, preferred with
   `parallelProcessing` because parallel completion makes the ratio
   non-deterministic), `group` for chunking, and `resumeStrategy` /
   `watermarkKey` / `watermarkExpression` for resume-from-position. Until now
   the Splitter's only failure knob was the binary `stopOnException`, and "what
   happens when 3 of 500 line items are bad?" is the commonest real Splitter
   question. `examples/09-routing-fundamentals/` already splits
   `jsonpath("$.line_items")` — a partial-failure variant is a natural
   extension. **Unconfirmed:** the release blog calls the chunking option
   `chunkSize`, the 4.22.0 route model exposes it as `group`; verify the Java
   DSL method name before writing.
2. **`allowedSchemes` — ch 14, small.** Same item as 7a but it is an addition
   too. Cross-reference ch 11's Dynamic Router, which gained the same option;
   4.22.1 further requires `allowPredicateFromMessage=true` before control
   messages may supply a predicate.
3. **Telemetry span shape and cardinality knobs — ch 27, small.** Beyond fixing
   7a, `includePatterns` and `disableCoreProcessors` give real control over
   trace cardinality, which the observability appendix skips. Fold in the 4.21
   `camel-micrometer` change: `MicrometerExchangeEventNotifier` now *always*
   emits `routeId`, empty string when absent — dashboards must handle
   `routeId=""`.
4. **`camel infra run observability` and `camel run --observe` — ch 27 or 39,
   medium.** A zero-config bundled stack (Prometheus, VictoriaTraces,
   VictoriaLogs, Perses). Present it as the five-second dev-loop option, *not*
   as a replacement for the LGTM appendix — it is a different stack.

Lower value, do opportunistically: Camel CLI installers plus the now-working
`camel run --jfr` (ch 39/31/35 — the flag was silently ignored before 4.22);
group-scoped variables (ch 08, only if there is already a variables section);
`camel-ai-tool` completions for ch 42, including that tool errors now go back
to the LLM rather than propagating to the route, which breaks `onException()`
around tool calls, and that `camel-spring-ai-tools` was *removed*; `camel-a2a`
as prose only, Preview, no example; the Spring Boot `spring.kafka.*` property
bridge (ch 20/32); p50/p95/p99 on the base counters from JMX (ch 17/18); and
canonical YAML DSL plus the new Java DSL model writer, which completes
round-trip DSL conversion (ch 19 — the most ch-19-relevant item in the range).

**One deliberate maybe:** a short security appendix. "Secure out of the box"
was the headline theme across both releases — JEP-290 deserialization filters,
Jackson polymorphic-type blocking, header filtering at transport boundaries,
dynamic URI allow-lists, Zip/Tar Slip prevention, credential masking,
`oauthProfile` on the HTTP consumers. The site has no security chapter. This is
worth writing **only as one argument** — that Camel's posture moved from "safe
if you configure it" to "safe by default" — not as a list of fifteen
mitigations. If it cannot be written that way, cut it and keep only the
`allowedSchemes` work.

## 8. Close-out document review  ☐

The final gate. A full read-through of every document in the repo, checking
both content and the links between documents.

- **Chapters** — all 43: front matter (title, order, part, description,
  duration), `part` values resolving to a real `_parts` entry, codetabs label
  order matching block order, diagram includes resolving, verification footers
  telling the truth
- **READMEs** — root `README.md`, `GETTING-STARTED.md`, `CONTRIBUTING.md`,
  `presentations/README.md`, and every per-example README: counts, version
  matrices, command lines, prerequisites
- **Linkages** — chapter ↔ example cross-references, part index pages,
  `_example_pages/`, the homepage card grid, deck ↔ chapter references, and
  every external link
- **Plans** — this file, `iteration-plan.md`, `reconciliation-plan.md`,
  `upgrade-camel-4.22-PLAN.md`, `upgrade-camel-4.22-RESUME.md`: retire what is
  spent, reconcile what disagrees
- **Prerequisites** — confirm item 6 landed everywhere prerequisites are
  listed, not just in chapter 00

Run `./scripts/validate-content.py` and `./scripts/check-chapter-parity.py` as
part of this, but do not mistake them for the review — they check endpoint URIs,
YAML shape, stale versions, config keys and dead links, not whether a sentence
is still true.

---

## Working notes

- Run `./scripts/build-all-examples.sh --with-tests` before merging; it routes
  Testcontainers at Podman automatically. Budget **~2 hours** — it is ~2 min per
  project and strictly sequential.
- **Testcontainers uses Docker here, not Podman**, despite the project
  documenting Podman everywhere. Check with
  `docker ps --filter name=testcontainers-ryuk`.
- The Citrus tests and the dev stack cannot both be up — both bind
  9092/6379/5432/6650. Symptom is "Local Docker Compose exited abnormally".
  Run `podman-compose -p eip -f examples/_infra/compose.yaml down` first.
- `search.maven.org` is **stale** — it reports Spring Boot topping out at
  3.5.3. Use `repo1.maven.org/.../maven-metadata.xml` for version checks.
- Never `pkill -f <pattern>` with the pattern in your own command line.
- Rootless podman containers are host java processes; a blanket kill over java
  PIDs takes down Kafka, Pulsar and Apicurio.
