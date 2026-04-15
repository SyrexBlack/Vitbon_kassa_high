# Step 7 v2 Emulator Smoke Lane Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a separate nightly/manual Android emulator smoke workflow that runs `connectedDebugAndroidTest` and publishes instrumentation artifacts without changing current required deterministic checks.

**Architecture:** Implement one isolated workflow file (`ci-emulator-smoke.yml`) with a single emulator job on `ubuntu-latest`, then add one minimal instrumentation smoke test to ensure the lane has a deterministic signal. Validate with manual workflow dispatch and run evidence capture while keeping branch protection unchanged in this phase.

**Tech Stack:** GitHub Actions YAML, Android Gradle (`connectedDebugAndroidTest`), Android Emulator Runner action, JDK 17, AndroidX instrumentation test framework

---

## File Structure and Responsibilities

- Create: `.github/workflows/ci-emulator-smoke.yml`
  - Responsibility: nightly/manual emulator smoke orchestration and artifact retention.
- Create: `android/app/src/androidTest/java/com/vitbon/kkm/SmokeStartupTest.kt`
  - Responsibility: minimal deterministic instrumentation smoke signal (app launch sanity check).
- Modify: `docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md`
  - Responsibility: append Step 7 v2 execution evidence (manual run URL/artifacts + observation policy start).

---

### Task 1: Create emulator smoke workflow scaffold

**Files:**
- Create: `.github/workflows/ci-emulator-smoke.yml`
- Test: `.github/workflows/ci-emulator-smoke.yml` (static + CLI workflow parse)

- [ ] **Step 1: Write failing checks for missing workflow (RED)**

```bash
test -f .github/workflows/ci-emulator-smoke.yml
```

- [ ] **Step 2: Run RED check and verify failure**

Run:
```bash
test -f .github/workflows/ci-emulator-smoke.yml; echo $?
```

Expected: non-zero exit code (file not present yet).

- [ ] **Step 3: Create minimal workflow with nightly + manual triggers and one emulator job (GREEN code)**

Create `.github/workflows/ci-emulator-smoke.yml` with exactly:

```yaml
name: CI Emulator Smoke

on:
  schedule:
    - cron: "17 2 * * *"
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ci-emulator-smoke-${{ github.ref }}
  cancel-in-progress: true

jobs:
  android-emulator-smoke:
    name: android-emulator-smoke
    runs-on: ubuntu-latest
    timeout-minutes: 45
    defaults:
      run:
        working-directory: android
    steps:
      - name: Checkout
        uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd

      - name: Set up JDK 17
        uses: actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Run emulator smoke tests
        uses: ReactiveCircus/android-emulator-runner@v2
        with:
          api-level: 34
          target: google_apis
          arch: x86_64
          profile: pixel_6
          ram-size: 4096M
          heap-size: 1024M
          disk-size: 4096M
          disable-animations: true
          emulator-options: -no-snapshot -no-boot-anim -gpu swiftshader_indirect -noaudio -camera-back none
          script: ./gradlew connectedDebugAndroidTest --no-daemon

      - name: Upload androidTest reports
        if: always()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a
        with:
          name: emulator-smoke-androidtest-reports
          path: |
            android/app/build/reports/androidTests/**
            android/app/build/outputs/androidTest-results/**
            android/app/build/outputs/logs/**
```

- [ ] **Step 4: Run GREEN checks for workflow contract**

Run:
```bash
grep -n "name: CI Emulator Smoke" .github/workflows/ci-emulator-smoke.yml
grep -n "workflow_dispatch:" .github/workflows/ci-emulator-smoke.yml
grep -n "cron: \"17 2 \* \* \*\"" .github/workflows/ci-emulator-smoke.yml
grep -n "name: android-emulator-smoke" .github/workflows/ci-emulator-smoke.yml
grep -n "connectedDebugAndroidTest --no-daemon" .github/workflows/ci-emulator-smoke.yml
```

Expected: all checks return matching lines.

- [ ] **Step 5: Validate workflow parsing in GitHub CLI**

Run:
```bash
gh workflow view .github/workflows/ci-emulator-smoke.yml --repo SyrexBlack/Vitbon_kassa_high >/dev/null
```

Expected: exit code 0.

- [ ] **Step 6: Commit Task 1**

```bash
git add .github/workflows/ci-emulator-smoke.yml
git commit -m "ci: add nightly emulator smoke workflow"
```

---

### Task 2: Add minimal instrumentation smoke test

**Files:**
- Create: `android/app/src/androidTest/java/com/vitbon/kkm/SmokeStartupTest.kt`
- Test: instrumentation test task execution command

- [ ] **Step 1: Write failing check for missing smoke test class (RED)**

```bash
test -f android/app/src/androidTest/java/com/vitbon/kkm/SmokeStartupTest.kt
```

- [ ] **Step 2: Run RED check and verify failure**

Run:
```bash
test -f android/app/src/androidTest/java/com/vitbon/kkm/SmokeStartupTest.kt; echo $?
```

Expected: non-zero exit code.

- [ ] **Step 3: Create minimal startup smoke instrumentation test (GREEN code)**

Create `android/app/src/androidTest/java/com/vitbon/kkm/SmokeStartupTest.kt` with exactly:

```kotlin
package com.vitbon.kkm

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeStartupTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun app_launches_without_crash() {
        // If MainActivity fails during launch, ActivityScenarioRule initialization will fail the test.
    }
}
```

- [ ] **Step 4: Run instrumentation task locally to verify GREEN**

Run:
```bash
cd android && ./gradlew connectedDebugAndroidTest --no-daemon
```

Expected: task completes successfully, smoke test included in executed androidTest suite.

- [ ] **Step 5: Commit Task 2**

```bash
git add android/app/src/androidTest/java/com/vitbon/kkm/SmokeStartupTest.kt
git commit -m "test(androidTest): add emulator startup smoke test"
```

---

### Task 3: Execute manual workflow run and capture Step 7 v2 evidence

**Files:**
- Modify: `docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md`
- Test: workflow run status + artifact presence + section existence checks

- [ ] **Step 1: Push branch and create PR for v2 emulator lane changes**

Run:
```bash
git push -u origin <step7-v2-branch>
gh pr create --base master --head <step7-v2-branch> --title "ci: add step7 v2 emulator smoke lane" --body "$(cat <<'EOF'
## Summary
- add isolated nightly/manual emulator smoke workflow
- add minimal instrumentation startup smoke test
- keep deterministic required gate unchanged

## Test plan
- [x] gh workflow view .github/workflows/ci-emulator-smoke.yml --repo SyrexBlack/Vitbon_kassa_high
- [ ] run workflow_dispatch for CI Emulator Smoke and verify artifacts
- [ ] verify required deterministic checks remain green on PR

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: PR URL is returned.

- [ ] **Step 2: Trigger manual workflow_dispatch and wait for completion**

Run:
```bash
gh workflow run "CI Emulator Smoke" --repo SyrexBlack/Vitbon_kassa_high --ref <step7-v2-branch>
gh run list --workflow "CI Emulator Smoke" --repo SyrexBlack/Vitbon_kassa_high --branch <step7-v2-branch> --limit 1
gh run watch <emulator-run-id> --repo SyrexBlack/Vitbon_kassa_high --exit-status
```

Expected: run completes with success.

- [ ] **Step 3: Verify emulator artifacts are published**

Run:
```bash
gh run view <emulator-run-id> --repo SyrexBlack/Vitbon_kassa_high
```

Expected: artifacts section includes `emulator-smoke-androidtest-reports`.

- [ ] **Step 4: Verify deterministic required checks on PR still pass**

Run:
```bash
gh pr checks <pr-number> --repo SyrexBlack/Vitbon_kassa_high
```

Expected PASS rows for:
- `backend-tests`
- `android-unit-tests`
- `android-assemble-debug`

- [ ] **Step 5: Merge PR and capture post-merge deterministic run evidence**

Run:
```bash
gh pr merge <pr-number> --repo SyrexBlack/Vitbon_kassa_high --merge --delete-branch
gh run list --workflow "CI Deterministic Gate" --repo SyrexBlack/Vitbon_kassa_high --branch master --limit 1
gh run view <post-merge-run-id> --repo SyrexBlack/Vitbon_kassa_high
```

Expected: post-merge deterministic run is success with all three jobs green.

- [ ] **Step 6: Append Step 7 v2 evidence section in spec file**

Append to `docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md`:

```markdown
## Step 7 v2 Emulator Smoke Evidence

Emulator lane PR:
- URL: `<STEP7_V2_PR_URL>`
- Workflow: `.github/workflows/ci-emulator-smoke.yml`
- Job: `android-emulator-smoke`

Manual smoke run (workflow_dispatch):
- Run: `<EMULATOR_RUN_ID>`
- URL: `<EMULATOR_RUN_URL>`
- Result: ✅ success
- Artifacts:
  - `emulator-smoke-androidtest-reports`

Observation policy started:
- Duration: 7 days
- Promotion rule: >= 6/7 nightly green and last 3 green in a row before required-check promotion.

Post-merge deterministic guardrail:
- Run: `<POST_MERGE_DETERMINISTIC_RUN_ID>`
- URL: `<POST_MERGE_DETERMINISTIC_RUN_URL>`
- Result:
  - `backend-tests` ✅
  - `android-unit-tests` ✅
  - `android-assemble-debug` ✅
```

- [ ] **Step 7: Verify spec section presence**

Run:
```bash
grep -n "## Step 7 v2 Emulator Smoke Evidence" docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md
grep -n "Observation policy started:" docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md
grep -n "emulator-smoke-androidtest-reports" docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md
```

Expected: all three checks match.

- [ ] **Step 8: Commit Task 3**

```bash
git add docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md
git commit -m "docs(ci): record step7 v2 emulator smoke evidence"
```

---

## Final Verification Gate

- [ ] Run:
```bash
git status --short
gh run list --workflow "CI Emulator Smoke" --repo SyrexBlack/Vitbon_kassa_high --limit 1
gh run list --workflow "CI Deterministic Gate" --repo SyrexBlack/Vitbon_kassa_high --branch master --limit 1
```

Expected:
- clean working tree;
- latest emulator smoke run visible with evidence;
- latest deterministic run green.

- [ ] Close epic and linked Beads tasks after verified merge/evidence:
```bash
bd close vitbon-kassa-6y4 --reason "Step7 v2 emulator smoke lane added, manual run verified with artifacts, and deterministic post-merge guardrail remained green"
```
