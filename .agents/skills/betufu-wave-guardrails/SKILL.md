---
name: betufu-wave-guardrails
description: Apply BETUFU-specific context, approval, worktree, scope, OpenAPI, verification, and frozen-review guardrails when planning, executing, verifying, or reviewing a GSD wave or plan in this repository.
---

# BETUFU Wave Guardrails

Use this skill as the operational router for BETUFU work. It complements
`AGENTS.md`, the active GSD workflow, and the current plan; it does
not replace them or grant permission to mutate the repository.

## Authority and approval

- Treat `AGENTS.md` as the canonical collaboration policy and execution
  contract. An optional `CLAUDE.md` may provide local Claude-specific guidance,
  but it cannot override `AGENTS.md` or block Antigravity/GSD execution.
- Default to REVISOR unless the user explicitly declares EXECUTOR.
- Never infer approval from a broad request. Obtain a specific approval before
  each material block, installation, dependency change, external call, test
  run, or infrastructure action.
- Treat worktree creation, stage, each commit, integration, cleanup, and push as
  separate Git approvals.
- Read-only inspection may prepare a proposal after a short announcement.

## Load the authoritative context

Before planning, resuming, executing, or reviewing:

1. Read `AGENTS.md` completely.
2. Read `CLAUDE.md` only when a Claude-specific executor is explicitly selected;
   its absence must not block Codex or Antigravity work.
3. Read `.planning/STATE.md`; use it as the current phase, wave, plan, and HEAD
   authority.
4. Read the active `PLAN.md`, its predecessor summaries, and the current and
   required predecessor `VALIDATION.md` files.
5. Read every `execution_context`, `required_reading`, template, or operational
   reference named by the plan or GSD workflow.
6. When Antigravity is involved, read
   `.agents/antigravity-wave-orchestration.md` completely.
7. Compare any handoff against live `git worktree list`, branch, HEAD, base SHA,
   timestamp, and `STATE.md`. Reject a stale handoff.
8. Use RepoWise MCP/LSP first when it is available. If it is unavailable, use
   native read-only inspection and state that the structural risk lookup was
   unavailable.

Do not load unrelated historical artifacts. Follow direct references from the
active plan and only expand context when evidence requires it.

## Select specialist skills

Read `.agents/skill-bundles.json` and load only the bundle matching the active
work. Bundles are routing guidance, not extra authorization. If a named skill
is unavailable, continue with the closest available guidance and record the
gap; do not install it implicitly.

## Present a preflight before mutation

Give the project owner a concise briefing containing:

- objective and expected observable behavior;
- approach and relevant technical concept;
- exact worktree, branch, base SHA, and current HEAD;
- exact files or directories allowed to change;
- commands planned, separated into read-only, tests, external actions, and Git;
- dependencies and checkpoints that must already be satisfied;
- generated-code and OpenAPI boundaries;
- impact, principal risks, stopping conditions, and expected result.

Do not start a material block until its approval matches this briefing.

## Keep execution confined

- Use one neutral worktree and branch per plan. Never implement in `master`.
- Run tasks from the same plan sequentially in that plan's worktree. Only
  independent plans from the same wave may run in parallel, with at most three
  workers.
- Before the first edit, confirm the worktree, branch, base SHA, clean starting
  state, plan allowlist, and task `<files>` list.
- Edit only approved paths. Stop on an unexpected change, scope drift, stale
  base, or a required architectural change outside the allowlist.
- Preserve changes made by the owner or another worker. Never revert them to
  make the task fit.
- A worker must not run Git mutations, installations, network, Docker,
  Testcontainers, or aggregate suites. It may run only explicitly approved,
  literal, fast focal commands from the task `<verify>` allowance.
- The orchestrator independently repeats worker tests and owns all material
  verification.

## Protect contracts and generated code

- Treat `backend/openapi/betufu.yaml` as a governed API contract, not merely
  documentation.
- Produce and inspect the candidate contract before replacing governed output.
  Require the plan's explicit digest checkpoint before generation.
- Generate the TypeScript client through the repository generator. Never edit
  generated client files manually.
- Verify governed before/after hashes and reconcile every generated path with
  the plan allowlist.
- Keep public DTOs minimal. Do not expose authentication identifiers, e-mail,
  enrollment data, HMAC values, secrets, or internal identifiers without an
  explicit requirement and review.

## Build evidence that can fail

Use the verification ladder required by the plan and approvals:

1. Reconcile `git status -uall` with the plan allowlist and task `<files>`.
2. Run focal tests for the changed behavior.
3. Prove new tests discriminate the old behavior when the plan requires a
   mutation or equivalent before/after proof. A test that passes on both sides
   is not evidence.
4. Verify framework-dependent claims empirically from actual artifacts,
   bytecode, or execution; do not rely on memory alone.
5. Run both aggregate repository suites before declaring implementation
   complete:
   `node scripts/maven.mjs -f backend/pom.xml verify` and
   `node --test scripts/*.test.mjs`.
6. Run Angular, production build, generator, Playwright, migration, security,
   or infrastructure checks when the active plan requires and the owner has
   approved them.
7. Read the actual reports and transcribe exact class, test, failure, error, and
   skipped counts. Never estimate totals.

Do not execute unapproved material commands merely because this skill lists
them. When evidence is unavailable, name the exact discriminating command and
report the limitation.

## Freeze reviewable evidence

Before external review, record:

- base SHA, HEAD, refs, index state, and `git status -uall`;
- every changed and untracked path;
- SHA-256 for every reviewed path;
- commands executed, exact results, and unverified areas;
- approved OpenAPI or generated-output digests when applicable.

Stop editing while the snapshot is under review. Any correction invalidates the
previous verdict and requires a new frozen snapshot and review.

## Handoff and pause

After an approved block, report in plain language:

- what changed and why;
- worktree, branch, base SHA, and HEAD;
- paths changed and their hashes;
- tests and validations executed, with exact results;
- deviations, residual risks, and what was not verified;
- the next proposed block and the separate approval it requires.

Then pause. Do not advance to the next block, commit, integrate, clean up, or
push by inference.

## Stop immediately when

- `STATE.md`, the handoff, the live branch, or HEAD disagree;
- the worktree contains unexplained changes;
- an edit would leave the plan allowlist;
- a dependency or human checkpoint lacks evidence;
- the correct solution requires a product or architecture decision;
- a test, contract digest, generated manifest, or frozen hash disagrees;
- credentials, secrets, or sensitive URLs could enter output or evidence.

Present sanitized evidence and request the smallest specific decision or
authorization needed to continue.
