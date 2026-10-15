# Process overview

A one-page map of how the project moves from research to a release: the
phases, the parallel tracks inside each, what depends on what, and where John
decides. The detail lives in [ROADMAP.md](ROADMAP.md) (items, gates) and
[.claude/agents/](../.claude/agents/) (how agents run an item); this page only
connects them. Last updated 2026-10-14 (Phase 1, tracks 1–6 done).

Why a flowchart and not a Gantt chart: the roadmap has no dates or durations,
only order and dependencies. A Gantt chart would invent a schedule, and its
bars can't show "this waits for that gate" or "John's decision feeds back
here". The flowchart below shows exactly those.

## 1. Phases, tracks and gates

How to read it:
- **Boxes** are phases; nodes side by side inside a phase run in parallel.
- **Orange hexagons** are gates: John approves before dependent work starts.
- **Purple rounded nodes** are other points where John gives input.
- **Solid arrows** are hard dependencies; **dotted arrows** are steering or
  inputs that don't block (e.g. a dump format reused later).
- The **green** node is where the project is now.

```mermaid
flowchart TB
    classDef gate fill:#fde3c4,stroke:#c46a00,stroke-width:2px,color:#000
    classDef human fill:#e8dcf7,stroke:#6a3fb0,stroke-width:2px,color:#000
    classDef done fill:#e6e6e6,stroke:#888,color:#444
    classDef now fill:#d4f5d4,stroke:#1f8a1f,stroke-width:3px,color:#000
    classDef work fill:#ffffff,stroke:#555,color:#000

    subgraph P0["Phase 0: Scaffolding (done)"]
        direction LR
        P0w["Gradle + planner-core boundary · CLI → IR JSON<br/>viewer + Playwright · server and client game tests"]:::done
    end
    G0{{"Gate 0: builds green,<br/>viewer renders the box<br/>(awaiting John)"}}:::gate
    P0w --> G0

    subgraph P1["Phase 1: Prior art research"]
        direction TB
        subgraph P1t["six tracks in parallel (done)"]
            direction LR
            T1["1 Minecraft mods"]:::done
            T2["2 GDMC"]:::done
            T3["3 PCG literature"]:::done
            T4["4 Worldgen internals"]:::done
            T5["5 Region overlays"]:::done
            T6["6 Aesthetics"]:::done
        end
        H1(["John: mark INDEX notes<br/>deep-dive / drop, any time"]):::human
        H17(["1.7 Early skim: John<br/>redirects tracks"]):::now
        S18["1.8 Landscape synthesis<br/>(researcher)"]:::work
        P1t -.-> H17
        H17 -.->|steers| P1t
        H1 -.->|steers| P1t
        P1t --> S18
        H17 --> S18
    end
    P0w -->|"dev env (0.5)"| T4
    G1{{"Gate 1: John picks building technique<br/>and the integration approach to spike<br/>(ADRs)"}}:::gate
    S18 --> G1

    subgraph P2["Phase 2: Worldgen spikes (throwaway code; findings notes only)"]
        direction TB
        subgraph P2s["spikes in parallel"]
            direction LR
            SP1["S1 Biome footprint"]:::work
            SP2["S2 Terrain probe"]:::work
            SP3["S3 Multi-start city"]:::work
            SP4["S4 Region map"]:::work
            SP5["S5 Terrain adaptation"]:::work
        end
        SP6["S6 Scale and cost<br/>(heavy lock)"]:::work
        A27["2.7 Worldgen ADR<br/>(researcher)"]:::work
        SP3 --> SP6
        P2s --> A27
        SP6 --> A27
    end
    G1 -->|integration approach| P2s
    G2{{"Gate 2: worldgen ADR<br/>(approach, size limits,<br/>determinism rules)"}}:::gate
    A27 --> G2

    subgraph P3["Phase 3: Planner core and three.js viewer"]
        direction TB
        I31["3.1 Building IR + JSON schema + fixture"]:::work
        subgraph P3p["planner track"]
            direction LR
            I32["3.2 Probes"]:::work --> I33["3.3 Generator v0"]:::work --> I34["3.4 CLI"]:::work
        end
        subgraph P3v["viewer track (works against the fixture)"]
            direction LR
            I35["3.5 Voxel mesh"]:::work --> I36["3.6–3.8 controls,<br/>invariant overlay, seed gallery"]:::work
        end
        I3t["3.9–3.11 property tests,<br/>determinism, diversity score"]:::work
        I31 --> P3p
        I31 --> P3v
        I33 --- I3t
    end
    G1 --> I31
    SP4 -.->|terrain/biome dump| I32
    G3{{"Gate 3: John reviews seed gallery<br/>and invariant report"}}:::gate
    P3p --> G3
    P3v --> G3
    I3t --> G3

    subgraph P4["Phase 4: Building generator depth"]
        direction TB
        I41["4.1 Plan: extension points<br/>(lead-coder)"]:::work
        subgraph P4a["in parallel"]
            direction LR
            I42["4.2 Massing"]:::work
            I43["4.3 Roofs"]:::work
            I44["4.4 Facades"]:::work
            I45["4.5 Interiors"]:::work
        end
        subgraph P4b["fan out, one item each"]
            direction LR
            I46["4.6 Building types"]:::work
            I47["4.7 Styles per biome"]:::work
        end
        I41 --> P4a --> P4b
    end
    G3 --> I41
    G1 -.->|chosen technique| I41
    G4{{"Gate 4: per building type,<br/>'would I believe a person built this?'"}}:::gate
    P4b --> G4

    subgraph P5["Phase 5: Settlements and cities (still outside Minecraft)"]
        direction LR
        I51["5.1–5.3 scale ladder,<br/>roads and lots, terrain"]:::work
        I54["5.4 Biome-aware style"]:::work
        I55["5.5 Viewer: 2D plan<br/>+ 3D fly-through"]:::work
        I56["5.6 Determinism from<br/>the region cell"]:::work
    end
    G4 --> P5
    G5{{"Gate 5: cities in 5+ biome mixes,<br/>one straddling a border"}}:::gate
    P5 --> G5

    subgraph P6["Phase 6: Minecraft integration and release"]
        direction LR
        I61["6.1–6.3 adapter, registration,<br/>terrain"]:::work
        I64["6.4–6.5 gameplay hooks, config"]:::work
        I66["6.6–6.8 compatibility,<br/>performance budget, client tests"]:::work
    end
    G5 --> P6
    G2 -->|integration approach| I61
    G6{{"Gate 6: fresh world on 3 seeds<br/>plays well for an hour"}}:::gate
    REL(["First public release"]):::human
    P6 --> G6 --> REL
```

Notes on the dependencies:
- The roadmap runs phases in order, but only the arrows above are hard
  dependencies. Phase 3 (pure Java and TypeScript) needs Gate 1's direction
  but not the worldgen ADR, so it can overlap Phase 2 if the coordinator has
  capacity; its recorded probe (3.2) needs S4's dump format. Gate 2 must pass
  before Phase 6 wires anything into worldgen.
- Spike outputs reused later (not drawn, to keep the chart readable): S4 needs
  the viewer from 0.4 for its 2D map, and its terrain/biome dumps feed Phase 5;
  S5 decides 6.3 (vanilla beard modes or our own grading); S3's
  order-independence test is re-implemented as a permanent regression test
  (Phase 3 or 6).
- Gate 0 doesn't block anything in practice: research started alongside
  Phase 0, and only track 4 needed the dev environment.

### Where John gives input

| When | What | Where |
|---|---|---|
| Any time in Phase 1 (and later research) | Mark notes `deep-dive` or `drop` | `docs/research/INDEX.md` |
| 1.7, after the first notes | Early skim: redirect tracks, flag deep-dives | tell the coordinator |
| Each gate (0–6) | Approve, or send back with comments | gate package in the run ledger |
| Gates 1 and 2 | Approve the ADRs that fix the technique and the worldgen approach | `docs/adr/` |
| Whenever items land | Judge looks and feel the agents can't | `docs/REVIEW.md` (`Result:` lines) |
| Whenever items land | Override cheap decisions agents made without you | `docs/ASSUMPTIONS.md` |
| Whenever reported | Review build-file and `flake.nix` changes before running them | agent summaries |
| Rarely | Anything costly to undo (GPU, EULA, credentials, pushing) | the coordinator asks |

## 2. How one item gets done

Inside every phase, each item goes through the same loop. The coordinator
keeps several of these in flight at once (soft cap about five coding items),
each in its own branch and worktree.

```mermaid
flowchart LR
    classDef gate fill:#fde3c4,stroke:#c46a00,stroke-width:2px,color:#000
    classDef human fill:#e8dcf7,stroke:#6a3fb0,stroke-width:2px,color:#000
    classDef agent fill:#ffffff,stroke:#555,color:#000

    C["Coordinator:<br/>brief + ledger row"]:::agent
    C -->|small or medium| CO["coder"]:::agent
    C -->|large or unclear| LP["lead-coder<br/>mode: plan"]:::agent
    LP -->|plan steps| CO
    C -->|deep research, ADR| RS["researcher"]:::agent
    C -->|research, tooling, docs| AS["assistant"]:::agent
    CO --> V{"verifier:<br/>tests, gallery<br/>before/after"}:::agent
    RS --> V
    AS -->|tooling, scripts| V
    AS -->|"research notes:<br/>coordinator merges"| M
    V -->|COMPLETE| M["squash-merge into<br/>run branch"]:::agent
    V -->|"INCOMPLETE / FAILING<br/>(about 2 rounds)"| FU["follow-up: resume coder,<br/>or lead-coder to diagnose"]:::agent
    FU --> V
    V -->|still blocked| B["record in ledger,<br/>move on"]:::agent
    M --> PB["non-breaking issues → Polish backlog<br/>looks/feel → REVIEW.md"]:::human
    M -->|phase done| MM["merge run branch<br/>into main + review package"]:::agent
    MM --> G{{"Gate: John decides"}}:::gate
```

Variations: research notes and docs merge straight from the run branch to
`main` as they land, so John sees them early; spike code is never merged,
only its findings note (`merge: docs-only`).
