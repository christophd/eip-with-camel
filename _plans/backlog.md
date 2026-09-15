---
title: "Backlog"
description: "The single live tracker for remaining work on the tutorial — post-upgrade cleanup, appendix examples, and the close-out document review."
render_with_liquid: false
---

This is the canonical list of what is left to do. `iteration-plan.md` is the
historical record of what shipped; this file is what is still open. Work the
sections in order and commit each item separately.

**Last reviewed:** 2026-09-15
**Branch:** `iteration/post-upgrade-cleanup` off `main` @ `d72dee7`

---

## Where the project stands

Feature-complete. 43 chapters covering all 65 EIP patterns, 33 example projects,
67 diagrams, 3 presentation decks.

The Camel 4.22 upgrade is merged to `main`: Camel **4.22.0** on all three
runtimes, Quarkus platform **3.39.3**, Spring Boot **4.1.1** (Spring 7.0.9),
Citrus **5.0.1**, JDK 25. 56/56 Maven projects build, 44 run integration tests
against live containers, 54/54 boot, 43/43 chapters verified. Full write-up in
`upgrade-camel-4.22-PLAN.md`.

Everything below is what that pass did *not* close.

---

## 1. LGTM stack — never started  ☐

The biggest real gap. Workstream 2 of the upgrade named LGTM explicitly and
never got to it.

All five images in `examples/_infra/compose.lgtm.yaml` are pinned and every tag
was confirmed to resolve in the registry — Grafana 13.2.1, Loki 3.7.7,
Mimir 3.2.1, Tempo 3.0.3, OTel collector 0.160.0. But **no LGTM container has
ever been created**, so `./scripts/setup-stack.sh --lgtm` is untested and
chapter 27's claims about dashboards and traces rest on nothing.

**Passing Citrus tests do not close this item.** Chapter 27's *routes* pass
their Citrus tests, and its footer currently claims verification on that basis
alone. That is the wrong evidence: the tests exercise route logic, not the
observability overlay. The stack has to come up and telemetry has to be
observed landing in Grafana, Mimir, Loki and Tempo before anything here is
called verified.

```bash
./scripts/setup-stack.sh --lgtm          # expect all services healthy
# then run 27-observability-stack and confirm telemetry actually lands:
./scripts/verify-example-runtime.sh 27-observability-stack quarkus 60
curl -s localhost:3000/api/health        # grafana
curl -s localhost:9009/ready             # mimir
curl -s localhost:3100/ready             # loki
curl -s localhost:3200/ready             # tempo
```

Then reconcile chapter 27 with what is actually observable, and rewrite its
footer to say what was seen, not what was inferred.

## 2. Presentation deck references Camel 3.0.0  ☐

`presentations/Apache Camel and Enterprise Integration (Final).pptx` contains
`3.0.0`. The other two decks (`eip-101.pptx`, `eip-201.pptx`) carry no version
strings.

Find the slide and decide whether the reference is historical (fine) or a stale
"current version" claim (fix). Note that **this deck has no generator** —
`presentations/src/` only builds `eip-101` and `eip-201`, and the file predates
that toolchain. Fixing it means unzipping the pptx, patching the slide XML, and
rezipping.

## 3. Stale handoff document  ☑ done

`_plans/upgrade-camel-4.22-HANDOFF.md` deleted — superseded by
`upgrade-camel-4.22-PLAN.md` and `upgrade-camel-4.22-RESUME.md`.

## 4. 432 chapter/example identifier divergences  ☐

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

## 5. Runnable examples for appendix gaps  ☐

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

## 6. Document the Testcontainers/Docker requirement  ☐

The project documents Podman everywhere and never mentions that **the Citrus
integration tests need Docker**. Testcontainers does not talk to the Podman
socket on this machine — `/run/user/<uid>/podman/podman.sock` does not exist
even though `systemctl --user is-active podman.socket` reports active — so the
44 projects with integration tests silently depend on a Docker daemon that no
document tells the reader to install.

Chapter 00 makes this worse: it has a "Why Podman over Docker?" section that
reads as though Docker is never needed.

- **Chapter 00** — add Docker to the prerequisites list alongside Podman, scoped
  to "only if you intend to run the integration tests". Amend the "Why Podman
  over Docker?" section so it does not contradict this. Add a version check to
  the verification block
- **Root `README.md`, `GETTING-STARTED.md`, `CONTRIBUTING.md`** — same addition
  wherever prerequisites are listed
- **Chapter 41 (Citrus Testing)** and the `37-testing-strategies` chapter and
  README — state the dependency at the point the reader runs the tests
- **`examples/_infra/README.md`** and `scripts/build-all-examples.sh` header —
  note that `--with-tests` requires Docker, and how to check
  (`docker ps --filter name=testcontainers-ryuk`)
- Also document the port contention: the Citrus tests and the dev stack cannot
  both be up — both bind 9092/6379/5432/6650

Worth investigating once while writing this up: whether pointing Testcontainers
at Podman via `DOCKER_HOST` and a rootful socket actually works here. If it
does, document that as the preferred path and keep Docker as the fallback. If
it does not, say so plainly so the next person does not spend an hour on it.

## 7. Close-out document review  ☐

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
