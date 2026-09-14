# Post-upgrade cleanup

**Branch:** `iteration/post-upgrade-cleanup` off `main` @ `d72dee7`
**Started:** 2026-09-14

The Camel 4.22 upgrade is merged to `main` (56/56 tests on Podman, 54/54 boot,
43/43 chapters verified, all PRs and issues closed). These are the four items
left over from it. Work them in order; commit each one separately.

---

## 1. LGTM stack — never started  ☐

The biggest real gap. Workstream 2 of the upgrade named LGTM explicitly.

All five images in `examples/_infra/compose.lgtm.yaml` are pinned and every tag
was confirmed to resolve in the registry — Grafana 13.2.1, Loki 3.7.7,
Mimir 3.2.1, Tempo 3.0.3, OTel collector 0.160.0. But **no LGTM container has
ever been created**, so `./scripts/setup-stack.sh --lgtm` is untested and
chapter 27's claims about dashboards and traces rest on nothing.

Chapter 27's *routes* do pass their Citrus tests. It is the observability
overlay that is unverified.

```bash
./scripts/setup-stack.sh --lgtm          # expect all services healthy
# then run 27-observability-stack and confirm telemetry actually lands:
./scripts/verify-example-runtime.sh 27-observability-stack quarkus 60
curl -s localhost:3000/api/health        # grafana
curl -s localhost:9009/ready             # mimir
curl -s localhost:3100/ready             # loki
curl -s localhost:3200/ready             # tempo
```

Then reconcile chapter 27 with what is actually observable, and update its
footer, which currently claims verification on the basis of the Citrus tests
alone.

## 2. Presentation deck references Camel 3.0.0  ☐

`presentations/Apache Camel and Enterprise Integration (Final).pptx` contains
`3.0.0`. The other two decks (`eip-101.pptx`, `eip-201.pptx`) carry no version
strings.

Find the slide, decide whether the reference is historical (fine) or a stale
"current version" claim (fix). Editing a pptx means unzipping, patching the
slide XML, and rezipping — or regenerating from the deck source if one exists.
Check for a generator under `scripts/` first.

## 3. Stale handoff document  ☑ done

`_plans/upgrade-camel-4.22-HANDOFF.md` deleted — superseded by
`upgrade-camel-4.22-PLAN.md` and `upgrade-camel-4.22-RESUME.md`.

## 4. 431 chapter/example identifier divergences  ☐

Lowest value, largest surface. `./scripts/check-chapter-parity.py` reports the
current count.

These were triaged during the upgrade and deliberately left: chapters routinely
show three variants of a pattern the example implements once (three
idempotent-receiver backing stores, two polling-consumer triggers, three
load-balancer strategies). Collapsing those would make the tutorial worse.
37 true 1:1 renames were already fixed.

**Before touching this, re-read that reasoning.** The useful work here is not
mass renaming — it is deciding, per chapter, whether the divergence misleads a
reader following along. Consider adding a short note to the chapters with the
largest gaps rather than changing identifiers.

---

## Working notes

- Run `./scripts/build-all-examples.sh --with-tests` before merging; it now
  routes Testcontainers at Podman automatically.
- The Citrus tests and the dev stack cannot both be up — both bind
  9092/6379/5432/6650.
- Never `pkill -f <pattern>` with the pattern in your own command line.
- Rootless podman containers are host java processes; a blanket kill over java
  PIDs takes down Kafka, Pulsar and Apicurio.
