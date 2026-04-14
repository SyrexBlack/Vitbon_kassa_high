# Step 7 Deterministic CI Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a deterministic GitHub Actions quality gate that runs backend tests, Android unit tests, and Android debug assemble for push/PR to `main` and `master`.

**Architecture:** Implement one workflow file with stable job IDs, explicit job boundaries, and artifact upload on failure. Build the workflow incrementally with RED→GREEN checks per job, then run the same proving Gradle commands locally to confirm the gate commands are reproducible before CI rollout.

**Tech Stack:** GitHub Actions YAML, Gradle Wrapper, JDK 17 (Temurin), Android SDK setup action, Kotlin/Spring Boot tests, Android JVM unit tests

---

## File Structure and Responsibilities

- Create: `.github/workflows/ci-deterministic.yml`
  - Responsibility: deterministic CI gate with three required jobs (`backend-tests`, `android-unit-tests`, `android-assemble-debug`) and artifact uploads.
- Modify: `docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md`
  - Responsibility: append implementation evidence after gate commands are verified locally.

---

### Task 1: Create workflow scaffold and backend test job

**Files:**
- Create: `.github/workflows/ci-deterministic.yml`
- Test: static workflow checks + local backend test run

- [ ] **Step 1: Write the failing test checks (RED target)**

```bash
grep -q "name: backend-tests" .github/workflows/ci-deterministic.yml
grep -q "gradlew.bat test --no-daemon" .github/workflows/ci-deterministic.yml
```

- [ ] **Step 2: Run the RED checks and verify they fail**

Run:
```bash
grep -q "name: backend-tests" .github/workflows/ci-deterministic.yml
echo $?
```

Expected: non-zero exit code because file/job does not exist yet.

- [ ] **Step 3: Write minimal workflow implementation with backend job (GREEN code)**

```yaml
name: CI Deterministic Gate

on:
  push:
    branches: [main, master]
  pull_request:
    branches: [main, master]

permissions:
  contents: read

concurrency:
  group: ci-deterministic-${{ github.ref }}
  cancel-in-progress: true

jobs:
  backend-tests:
    name: backend-tests
    runs-on: windows-latest
    timeout-minutes: 25
    defaults:
      run:
        shell: cmd
        working-directory: backend
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Run backend tests
        run: gradlew.bat test --no-daemon

      - name: Upload backend test artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: backend-test-artifacts
          path: |
            backend/build/reports/tests/**
            backend/build/test-results/**
```

- [ ] **Step 4: Re-run static checks and verify GREEN for backend job**

Run:
```bash
grep -q "name: backend-tests" .github/workflows/ci-deterministic.yml
grep -q "gradlew.bat test --no-daemon" .github/workflows/ci-deterministic.yml
```

Expected: both checks pass (exit code 0).

- [ ] **Step 5: Run backend proving command locally**

Run:
```bash
cd backend && ./gradlew.bat test --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Task 1**

```bash
git add .github/workflows/ci-deterministic.yml
git commit -m "ci: add deterministic backend test gate job"
```

---

### Task 2: Add Android unit test job

**Files:**
- Modify: `.github/workflows/ci-deterministic.yml`
- Test: static workflow checks + local Android unit test run

- [ ] **Step 1: Write failing check for missing android unit job (RED target)**

```bash
grep -q "name: android-unit-tests" .github/workflows/ci-deterministic.yml
grep -q "gradlew.bat testDebugUnitTest --no-daemon" .github/workflows/ci-deterministic.yml
```

- [ ] **Step 2: Run RED check and verify failure**

Run:
```bash
grep -q "name: android-unit-tests" .github/workflows/ci-deterministic.yml
echo $?
```

Expected: non-zero exit code (job absent before implementation).

- [ ] **Step 3: Extend workflow with android unit test job (GREEN code)**

```yaml
name: CI Deterministic Gate

on:
  push:
    branches: [main, master]
  pull_request:
    branches: [main, master]

permissions:
  contents: read

concurrency:
  group: ci-deterministic-${{ github.ref }}
  cancel-in-progress: true

jobs:
  backend-tests:
    name: backend-tests
    runs-on: windows-latest
    timeout-minutes: 25
    defaults:
      run:
        shell: cmd
        working-directory: backend
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle
      - name: Run backend tests
        run: gradlew.bat test --no-daemon
      - name: Upload backend test artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: backend-test-artifacts
          path: |
            backend/build/reports/tests/**
            backend/build/test-results/**

  android-unit-tests:
    name: android-unit-tests
    runs-on: windows-latest
    timeout-minutes: 40
    defaults:
      run:
        shell: cmd
        working-directory: android
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Run Android unit tests
        run: gradlew.bat testDebugUnitTest --no-daemon

      - name: Upload Android unit test artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: android-unit-test-artifacts
          path: |
            android/app/build/reports/tests/**
            android/app/build/test-results/**
```

- [ ] **Step 4: Re-run static checks and verify GREEN for android unit job**

Run:
```bash
grep -q "name: android-unit-tests" .github/workflows/ci-deterministic.yml
grep -q "gradlew.bat testDebugUnitTest --no-daemon" .github/workflows/ci-deterministic.yml
```

Expected: both checks pass.

- [ ] **Step 5: Run Android unit test proving command locally**

Run:
```bash
cd android && ./gradlew.bat testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Task 2**

```bash
git add .github/workflows/ci-deterministic.yml
git commit -m "ci: add deterministic android unit test gate job"
```

---

### Task 3: Add Android assemble debug job with dependency

**Files:**
- Modify: `.github/workflows/ci-deterministic.yml`
- Test: static dependency checks + local Android assemble run

- [ ] **Step 1: Write failing checks for missing assemble job and dependency (RED target)**

```bash
grep -q "name: android-assemble-debug" .github/workflows/ci-deterministic.yml
grep -q "needs: \[android-unit-tests\]" .github/workflows/ci-deterministic.yml
grep -q "gradlew.bat assembleDebug --no-daemon" .github/workflows/ci-deterministic.yml
```

- [ ] **Step 2: Run RED checks and verify failure**

Run:
```bash
grep -q "name: android-assemble-debug" .github/workflows/ci-deterministic.yml
echo $?
```

Expected: non-zero exit code before job is added.

- [ ] **Step 3: Write final workflow with assemble job (GREEN code)**

```yaml
name: CI Deterministic Gate

on:
  push:
    branches: [main, master]
  pull_request:
    branches: [main, master]

permissions:
  contents: read

concurrency:
  group: ci-deterministic-${{ github.ref }}
  cancel-in-progress: true

jobs:
  backend-tests:
    name: backend-tests
    runs-on: windows-latest
    timeout-minutes: 25
    defaults:
      run:
        shell: cmd
        working-directory: backend
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle
      - name: Run backend tests
        run: gradlew.bat test --no-daemon
      - name: Upload backend test artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: backend-test-artifacts
          path: |
            backend/build/reports/tests/**
            backend/build/test-results/**

  android-unit-tests:
    name: android-unit-tests
    runs-on: windows-latest
    timeout-minutes: 40
    defaults:
      run:
        shell: cmd
        working-directory: android
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
      - name: Run Android unit tests
        run: gradlew.bat testDebugUnitTest --no-daemon
      - name: Upload Android unit test artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: android-unit-test-artifacts
          path: |
            android/app/build/reports/tests/**
            android/app/build/test-results/**

  android-assemble-debug:
    name: android-assemble-debug
    runs-on: windows-latest
    timeout-minutes: 40
    needs: [android-unit-tests]
    defaults:
      run:
        shell: cmd
        working-directory: android
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
      - name: Assemble Android debug APK
        run: gradlew.bat assembleDebug --no-daemon
      - name: Upload Android assemble artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: android-assemble-artifacts
          path: |
            android/app/build/outputs/apk/debug/**
            android/app/build/reports/**
```

- [ ] **Step 4: Re-run static checks and verify GREEN for assemble dependency**

Run:
```bash
grep -q "name: android-assemble-debug" .github/workflows/ci-deterministic.yml
grep -q "needs: \[android-unit-tests\]" .github/workflows/ci-deterministic.yml
grep -q "gradlew.bat assembleDebug --no-daemon" .github/workflows/ci-deterministic.yml
```

Expected: all checks pass.

- [ ] **Step 5: Run Android assemble proving command locally**

Run:
```bash
cd android && ./gradlew.bat assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Task 3**

```bash
git add .github/workflows/ci-deterministic.yml
git commit -m "ci: add deterministic android assemble debug gate job"
```

---

### Task 4: Verify full deterministic gate contract and document evidence

**Files:**
- Modify: `docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md`
- Test: combined proving command run + spec evidence presence check

- [ ] **Step 1: Run full proving commands in one fresh pass**

Run:
```bash
cd backend && ./gradlew.bat test --no-daemon
cd ../android && ./gradlew.bat testDebugUnitTest --no-daemon
./gradlew.bat assembleDebug --no-daemon
```

Expected: all three commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 2: Append implementation evidence block to Step 7 design spec**

```markdown
## Implementation Evidence (Step 7 v1)

Executed locally on current branch:
- `cd backend && ./gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`
- `cd android && ./gradlew.bat testDebugUnitTest --no-daemon` → `BUILD SUCCESSFUL`
- `cd android && ./gradlew.bat assembleDebug --no-daemon` → `BUILD SUCCESSFUL`

Workflow file created:
- `.github/workflows/ci-deterministic.yml`

Deterministic jobs present:
- `backend-tests`
- `android-unit-tests`
- `android-assemble-debug`
```

- [ ] **Step 3: Verify spec evidence section exists**

Run:
```bash
grep -q "## Implementation Evidence (Step 7 v1)" docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md
grep -q "ci-deterministic.yml" docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md
```

Expected: both checks pass.

- [ ] **Step 4: Commit Task 4**

```bash
git add docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md .github/workflows/ci-deterministic.yml
git commit -m "docs(ci): add deterministic gate implementation evidence"
```

---

## Final Verification Gate

- [ ] Run: `git status --short` and verify only expected files changed.
- [ ] Run: `bd ready` and verify next actionable task visibility after closing current tasks.
- [ ] Run: `bd close vitbon-kassa-<id> --reason "Done"` for each completed sub-task (replace `<id>` with actual issue suffix).
