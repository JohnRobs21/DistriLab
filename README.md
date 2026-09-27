# DistriLab — Unstructured Distributed Computing Cluster

A small Java RMI–based distributed system: worker nodes form an unstructured
peer network, elect a coordinator among themselves, and the coordinator
splits incoming jobs (MAX, PRIMESUM, PRIMECOUNT) across its neighbours.

Written and run as a VS Code project — the six launch configs in
`launch.json` are the intended way to start everything.

## Requirements

- **VS Code** with the **Extension Pack for Java** (Microsoft) installed —
  this gives you the Run/Debug buttons and reads `launch.json` automatically.
- **JDK 8 or later** on your machine, pointed to from VS Code (Java
  extension will prompt you to configure this if it isn't already).
- No external libraries — plain `java.rmi`, `java.util.concurrent`, and
  `javax.swing` only.

## Project structure

common/
  BootstrapService.java   RMI interface for the Bootstrap Node
  WorkerService.java      RMI interface for a worker (election, jobs, tasks)
  CoordinatorService.java (defined, not currently used separately from WorkerService)
  JobPayload.java         Serializable job data (range or number array)
bootstrap/
  BootstrapNode.java      main() — starts the registry + BootstrapService
  BootstrapImpl.java      BootstrapService implementation
worker/
  WorkerNode.java         main() — starts a worker, registers, connects
  WorkerImpl.java         WorkerService implementation (election + jobs)
client/
  ClientGUI.java          Swing client — connect, submit, view results
.vscode/
  launch.json             the 6 run configs described below


## Running it — all through VS Code

Open the project folder in VS Code. Open the **Run and Debug** panel
(`Ctrl+Shift+D` / `Cmd+Shift+D`), and you'll see a dropdown at the top listing
the six configs from `launch.json`. VS Code compiles automatically on Run, so
there's no separate build step.

Launch them **in this exact order**, waiting a couple of seconds between each
so the previous one finishes starting before the next one registers:

| # | Config name | What it does |
|---|---|---|
| 1 | **Launch Bootstrap Node** | Starts the RMI registry on port 1099 and binds `BootstrapService`. Start this first and leave it running. |
| 2 | **Launch Worker 101** | Starts worker ID 101, registers with the bootstrap node on port 1099. |
| 3 | **Launch Worker 102** | Starts worker ID 102, same registry. |
| 4 | **Launch Worker 103** | Starts worker ID 103, same registry. |
| 5 | **Launch Worker 104** | Starts worker ID 104, same registry. |
| 6 | **Launch Client GUI** | Opens the Swing client. Run this last, once you've seen a coordinator elected in one of the worker consoles. |

To run each one: select it from the dropdown, then click the green ▶ Start
Debugging button (or press `F5`). Each launches in its own **Debug Console**
tab at the bottom of the window — switch between tabs to watch each
process's output, since they all log independently.

All four worker configs and the bootstrap config need to be **running
simultaneously** (don't stop one to start the next) — VS Code lets you have
multiple debug sessions active at once; they'll stack up in the Run and
Debug call-stack panel on the left.

Each worker's Debug Console accepts one command for manual testing — click
into that tab and type:
- `elect` + Enter — force that worker to start a new leader election.

## Using the client once it's open

1. Enter a worker's RMI URL, e.g. `rmi://localhost:1099/Worker_101`, and
   click **Connect** — it doesn't have to be the current coordinator; if the
   worker you connect to isn't the leader, its `submitJob` forwards the call
   on to whichever worker is.
2. Pick a job type — **MAX** / **PRIMECOUNT** take comma-separated numbers
   (or **Load CSV...**); **PRIMESUM** takes a start/end range.
3. Click **Submit Job**. Each submission runs on its own background thread,
   so you can queue several jobs without freezing the UI, and results appear
   in the table as they complete.

## Stopping everything

Use the red ■ Stop button on each debug session in the Run and Debug panel
(or the Debug Console's own stop control), stopping the client and workers
before the bootstrap node last. Stopping a worker process doesn't cleanly
`unregisterWorker()` unless you exit it via its own shutdown path — expect
the bootstrap node's active-worker list to still mention a worker you just
force-stopped from VS Code, which is fine for a demo but worth knowing if a
job submission afterward errors out referencing it.

## Design notes (useful for the Part 1 report)

- **Leader election**: a candidate-carrying flood. `initiateElection()`
  starts with the initiator as the sole candidate and a `visitedNodes` list
  containing just itself. Each worker that receives the message once (guarded
  by a `processedElections` set keyed on `electionId`) compares its own JAC
  against the carried candidate — lower JAC wins, ties broken by higher ID —
  then forwards the (possibly updated) candidate onward to any neighbour not
  already in `visitedNodes`. Whoever ends up as the best candidate announces
  itself directly.
- **JAC**: incremented once per **whole job** submitted while leading (not
  once per fragment) — see the `this.jobAllocationCounter++` in `submitJob()`.
  Worth stating this explicitly as your interpretation in the report, since
  the spec wording is a little open between "per job" and "per fragment".
- **Job distribution**: the leader splits work across its own **immediate
  neighbours plus itself** — not the full set of workers reachable during the
  election. Worth a sentence in the report on why (simpler to reason about,
  avoids needing to carry/store a full roster) and its trade-off (a leader
  with few neighbours under-uses distant workers).
- **Term limit**: after 5 jobs, the leader steps down and starts a new
  election in a background thread (`jobsInCurrentTerm >= 5`).
- **Bootstrap Node**: only ever stores `id -> RMI URL` and hands back a
  random existing URL on registration — it never sees an election or a job,
  which is the basis for arguing it centralises discovery but not operation.

## AI tools used

Claude and Gemini was used to fix errors and create this README file.