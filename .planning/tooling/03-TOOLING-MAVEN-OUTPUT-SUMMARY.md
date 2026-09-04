---
plan_id: 03-TOOLING-MAVEN-OUTPUT
status: complete-pending-git
completed: 2026-09-04
subsystem: developer-tooling
tags: [maven, output-masking, diagnostics, node]

requires:
  - plan: 03-25
    provides: clean local baseline for the Phase 3 tooling follow-up
provides:
  - bounded Maven stdout/stderr collection with short terminal summaries
  - failure diagnostics retained in a temporary log without full terminal replay
  - deterministic Node tests for chunking, test totals, redaction, limits and exit codes
affects: [developer-workflow, local-verification]

actuals:
  tasks: 5
  commits: 3
  tooling_paths: 3
  documentation_paths: 3
  mechanical_formatting_paths: 16

commits:
  - d3b9c32 feat(tooling): mask Maven output and preserve diagnostics
  - 6d29a00 style(backend): apply Spotless formatting required by verify
  - docs(tooling): document Maven output masking (this commit)

implementation:
  - "scripts/maven.mjs captures child stdout/stderr, preserves stdin, shell:false, platform wrapper selection and exit/signal behavior."
  - "scripts/lib/maven-output.mjs keeps bounded state, writes the complete diagnostic log through a callback, counts Surefire/Failsafe totals, strips ANSI and redacts sensitive values."
  - "Failure output is limited to the summary, at most ten useful lines and the temporary log path; successful logs are removed after the summary."
  - "docs/development.md explains the short success output and how to locate a retained failure log."

verification:
  - command: node --check scripts/maven.mjs
    result: pass
  - command: node --test scripts/maven-output.test.mjs
    result: pass (7/7)
  - command: node scripts/maven.mjs -f backend/pom.xml -DskipTests validate
    result: pass
  - command: node scripts/maven.mjs -f backend/pom.xml verify
    result: pass (1472/1472; 0 failures, 0 errors, 0 skipped)
    environment: "test-only BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1 and BETUFU_MATRICULA_HMAC_KEY_V1"
  - command: node --test scripts/*.test.mjs
    result: pass (638/638; 9 suites)

deviations:
  - "The first aggregate verify lacked the required local HMAC variables and produced 67 Spring ApplicationContext bootstrap errors; no source regression was implicated."
  - "After the HMAC environment was supplied, Spotless found backend formatting drift. The approved mechanical formatter produced effective diffs in 16 Java paths, committed separately from the tooling."
  - "Temporary failure logs were retained for local diagnosis; cleanup remains a separate operation."

scope:
  - "No Java behavior, Spring contract, database migration, OpenAPI contract, generated client, dependency, CI or hook was intentionally changed."
  - "The Angular changes from the concurrent 03-26 work remain outside these commits."
  - "No stage, push, merge, rebase or cleanup beyond the three authorized local commits was performed."

next_step: "Integrate the three local commits only after separate approval for the desired Git integration operation."
