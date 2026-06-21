# ULW Notepad: Production Verification Suite

## Objective
Continue Vitbon KKM production-readiness work for bead `vitbon-kassa-nhy`: create a single production verification suite command/checklist that reports objective pass/fail evidence for blocker contours.

## Skill Survey
- `omo:ulw-loop`: used because the user explicitly asked `omo ulw-loop продолжи`; requires evidence-bound criteria, manual QA, and reviewer gate.
- `omo:programming`: consulted as coding discipline guidance, though this task is Kotlin/Gradle-heavy rather than the listed Python/Rust/TypeScript/Go focus.
- `omo:debugging`: reserve for real runtime/build failures or hanging Gradle/CLI processes.
- `omo:review-work`: final verification will instead use mandatory `codex-ultrawork-reviewer` from ULW, so no separate review-work orchestration unless reviewer asks.
- `omo:lsp`: use if Kotlin LSP diagnostics are available after edits; Gradle tests remain the primary compiler signal.

## Scope Sizing
- Surfaces: Android Gradle test command, backend Gradle test command, release/checklist docs or generated evidence, bead state.
- Files expected: Android test/build scripts or verification script, docs/evidence output, possibly backend invocation script; exact files to be selected after plan/search.
- Steps: inspect existing blocker tests, design suite, write RED test for missing command/evidence, implement command, run unit/integration, run manual QA channel, review.
- Non-trivial: multi-surface, multi-file, architecture decision for one-command suite. Plan agent required.

## Binding Success Criteria
Deliverable: one production verification suite command/checklist for go-live blockers with honest software-ready vs hardware-required status.

### C001 Happy Path: One-command software blocker verification
- Automated test first: add a test that fails until the verification command/spec includes blocker sections for fiscal adapter software tests, RBAC, encryption/security, root policy, audit trail, and status gating.
- Manual-QA channel: tmux.
- Scenario invocation: `tmux new-session -d -s ulw-qa-prodverify-c001 "cmd.exe /c <verification-command>"`
- PASS observable: transcript contains all blocker sections and exits with success for software-verifiable checks.
- Evidence: `.omo/ulw-loop/evidence/G002-C001-prodverify-tmux.txt`, plus RED/GREEN test logs.

### C002 Edge: Hardware-required MSPOS-K evidence is not falsely passed
- Automated test first: add a test that fails until MSPOS-K physical smoke is represented as `HARDWARE_REQUIRED` / non-software-pass when no device evidence is present.
- Manual-QA channel: tmux.
- Scenario invocation: same verification command with no hardware evidence fixture.
- PASS observable: transcript explicitly lists MSPOS-K physical smoke as hardware-required/pending while the command still distinguishes software suite status.
- Evidence: `.omo/ulw-loop/evidence/G002-C002-hardware-required-tmux.txt`, plus RED/GREEN test logs.

### C003 Regression: Backend Java 17 report verification remains integrated
- Automated test first: add/extend test or suite assertion that backend report command uses Java 17 path/setting and includes `CheckServiceSalesReportTest`.
- Manual-QA channel: tmux.
- Scenario invocation: `tmux new-session -d -s ulw-qa-prodverify-c003 "cmd.exe /c <verification-command-or-backend-step>"`
- PASS observable: transcript shows backend report test command using Java 17 and passing.
- Evidence: `.omo/ulw-loop/evidence/G002-C003-backend-java17-tmux.txt`, plus RED/GREEN test logs.

## Open Risks
- Physical MSPOS-K/FN/OFD cannot be proven without hardware. The suite must not mark that as passed.
- Existing uncommitted payment-slice changes are present; do not revert them.
- Windows `cmd.exe` quoting is fragile. Prefer simple commands and short Java path `C:\Progra~1\Java\jdk-17`.

## Evidence Log
- RED: `.omo/ulw-loop/evidence/G002-prodverify-test-red.txt` — `verify-phase-b.ps1 -PlanOnly` behavior missing before implementation.
- GREEN: `.omo/ulw-loop/evidence/G002-prodverify-test-green.txt` and `G002-prodverify-test-green-2.txt` — plan-only blocker matrix/report regression passed.
- RED: `.omo/ulw-loop/evidence/G002-prodverify-command-report-red.txt` — report command interpolation bug caught.
- GREEN: `.omo/ulw-loop/evidence/G002-prodverify-command-report-green.txt` — command interpolation regression passed.
- C001 manual CLI artifact: `.omo/ulw-loop/evidence/G002-C001-prodverify-cli-transcript.txt`; report `.omo/ulw-loop/evidence/G002-C001-prodverify-report.md`.
- C002 manual CLI artifact: `.omo/ulw-loop/evidence/G002-C002-hardware-required-tmux.txt` plus C001 transcript/report; `tmux` unavailable on Windows (`where tmux` returned no executable).
- C003 manual CLI artifact: `.omo/ulw-loop/evidence/G002-C003-prodverify-cli-transcript-2.txt`; report `.omo/ulw-loop/evidence/G002-C003-prodverify-report-2.md`.
- Reviewer blocker: adb online device detection could falsely mark MSPOS-K smoke PASS. Fixed by adding explicit `-HardwareEvidencePath`; PASS now requires an evidence file containing `MSPOS-K physical smoke PASS`.
- RED: `.omo/ulw-loop/evidence/G002-prodverify-hardware-evidence-red.txt` — source allowed adb online device to produce PASS.
- GREEN: `.omo/ulw-loop/evidence/G002-prodverify-hardware-evidence-green.txt` — hardware evidence gate present and adb-only PASS removed.
- Fresh post-fix C001/C002: `.omo/ulw-loop/evidence/G002-C001-prodverify-cli-transcript-2.txt`; report `.omo/ulw-loop/evidence/G002-C001-prodverify-report-2.md`.
- Fresh post-fix C003: `.omo/ulw-loop/evidence/G002-C003-prodverify-cli-transcript-3.txt`; report `.omo/ulw-loop/evidence/G002-C003-prodverify-report-3.md`.
