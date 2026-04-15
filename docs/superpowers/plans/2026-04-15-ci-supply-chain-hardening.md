# CI Supply-Chain Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden deterministic CI supply-chain by pinning all GitHub Actions to immutable commit SHAs and enabling Dependabot updates for github-actions.

**Architecture:** Keep the current deterministic workflow topology unchanged, but replace mutable action tags with immutable SHA pins. Add a repository-level Dependabot config for weekly github-actions updates, then verify protection checks stay green on PR and post-merge runs.

**Tech Stack:** GitHub Actions workflow YAML, Dependabot YAML, GitHub CLI (`gh`), branch protection required checks

---

## File Structure and Responsibilities

- Modify: `.github/workflows/ci-deterministic.yml`
  - Responsibility: deterministic gate execution with immutable action references.
- Create: `.github/dependabot.yml`
  - Responsibility: weekly github-actions dependency update PRs.
- Modify: `docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md`
  - Responsibility: append external hardening evidence (PR/run URLs + acceptance criteria mapping).

---

### Task 1: Pin deterministic workflow actions to immutable SHAs

**Files:**
- Modify: `.github/workflows/ci-deterministic.yml`
- Test: `.github/workflows/ci-deterministic.yml` (static assertions)

- [ ] **Step 1: Write failing static checks (RED) for mutable action tags**

```bash
grep -n "actions/checkout@v" .github/workflows/ci-deterministic.yml
grep -n "actions/setup-java@v" .github/workflows/ci-deterministic.yml
grep -n "actions/upload-artifact@v" .github/workflows/ci-deterministic.yml
grep -n "android-actions/setup-android@v" .github/workflows/ci-deterministic.yml
```

- [ ] **Step 2: Run RED checks and verify matches exist before pinning**

Run:
```bash
grep -n "actions/checkout@v" .github/workflows/ci-deterministic.yml
```

Expected: at least one matching line (workflow still uses tag reference before change).

- [ ] **Step 3: Replace tag references with approved immutable SHAs (GREEN code)**

Use these exact replacements:

- `actions/checkout@v6.0.2` → `actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd`
- `actions/setup-java@v5.2.0` → `actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654`
- `actions/upload-artifact@v7.0.1` → `actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a`
- `android-actions/setup-android@v4.0.1` → `android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699`

Expected final snippets in `.github/workflows/ci-deterministic.yml`:

```yaml
- name: Checkout
  uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd

- name: Set up JDK 17
  uses: actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654

- name: Set up Android SDK
  uses: android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699

- name: Upload backend test artifacts
  if: always()
  uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a
```

- [ ] **Step 4: Run GREEN checks confirming no mutable tags remain**

Run:
```bash
! grep -n "actions/checkout@v" .github/workflows/ci-deterministic.yml
! grep -n "actions/setup-java@v" .github/workflows/ci-deterministic.yml
! grep -n "actions/upload-artifact@v" .github/workflows/ci-deterministic.yml
! grep -n "android-actions/setup-android@v" .github/workflows/ci-deterministic.yml
grep -nE "uses: .+@[0-9a-f]{40}$" .github/workflows/ci-deterministic.yml
```

Expected:
- first four commands return no matches;
- final command returns all `uses:` lines with 40-hex SHA.

- [ ] **Step 5: Validate workflow syntax with GitHub CLI**

Run:
```bash
gh workflow view .github/workflows/ci-deterministic.yml --repo SyrexBlack/Vitbon_kassa_high >/dev/null
```

Expected: exit code `0`.

- [ ] **Step 6: Commit Task 1**

```bash
git add .github/workflows/ci-deterministic.yml
git commit -m "ci: pin deterministic workflow actions by commit sha"
```

---

### Task 2: Add Dependabot updates for github-actions

**Files:**
- Create: `.github/dependabot.yml`
- Test: `.github/dependabot.yml` (static assertions)

- [ ] **Step 1: Write failing check for missing Dependabot config (RED)**

```bash
test -f .github/dependabot.yml
```

- [ ] **Step 2: Run RED check and verify it fails before file creation**

Run:
```bash
test -f .github/dependabot.yml; echo $?
```

Expected: non-zero exit code.

- [ ] **Step 3: Create minimal Dependabot config (GREEN code)**

Create `.github/dependabot.yml` with exactly:

```yaml
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

- [ ] **Step 4: Run GREEN checks for required keys**

Run:
```bash
grep -n "^version: 2$" .github/dependabot.yml
grep -n "package-ecosystem: \"github-actions\"" .github/dependabot.yml
grep -n "directory: \"/\"" .github/dependabot.yml
grep -n "interval: \"weekly\"" .github/dependabot.yml
```

Expected: all checks match exactly one line.

- [ ] **Step 5: Commit Task 2**

```bash
git add .github/dependabot.yml
git commit -m "ci: add dependabot updates for github actions"
```

---

### Task 3: Validate hardening via PR and post-merge runs, then document evidence

**Files:**
- Modify: `docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md`
- Test: required-check outputs + spec assertions

- [ ] **Step 1: Push branch and create PR for hardening changes**

Run:
```bash
git push -u origin <hardening-branch>
gh pr create --base master --head <hardening-branch> --title "ci: harden deterministic workflow supply chain" --body "$(cat <<'EOF'
## Summary
- pin deterministic CI actions to immutable commit SHAs
- add weekly Dependabot updates for github-actions
- keep deterministic job contract unchanged

## Test plan
- [x] gh workflow view .github/workflows/ci-deterministic.yml --repo SyrexBlack/Vitbon_kassa_high
- [ ] required CI checks pass on this PR

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: PR URL is returned.

- [ ] **Step 2: Verify required checks are green on the PR**

Run:
```bash
gh pr checks <pr-number> --repo SyrexBlack/Vitbon_kassa_high
```

Expected PASS rows for:
- `backend-tests`
- `android-unit-tests`
- `android-assemble-debug`

- [ ] **Step 3: Merge PR and verify post-merge deterministic run**

Run:
```bash
gh pr merge <pr-number> --repo SyrexBlack/Vitbon_kassa_high --merge --delete-branch
gh run list --workflow "CI Deterministic Gate" --repo SyrexBlack/Vitbon_kassa_high --branch master --limit 1
gh run view <post-merge-run-id> --repo SyrexBlack/Vitbon_kassa_high
```

Expected:
- PR state = `MERGED`;
- latest `master` run conclusion = `success`;
- all three deterministic jobs green.

- [ ] **Step 4: Append hardening evidence to Step 7 spec**

Append this section to `docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md` (replace placeholders):

```markdown
## Supply-Chain Hardening Evidence (Step 7)

Hardening PR:
- URL: `<PR_URL>`
- Scope: pin workflow actions to immutable commit SHAs + add `.github/dependabot.yml` for weekly github-actions updates.

Pinned actions in `ci-deterministic.yml`:
- `actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd`
- `actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654`
- `android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699`
- `actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a`

Post-merge deterministic run:
- Run: `<RUN_ID>`
- URL: `<RUN_URL>`
- Result:
  - `backend-tests` ✅
  - `android-unit-tests` ✅
  - `android-assemble-debug` ✅

Acceptance criteria coverage:
- AC1 (`uses` pinned to immutable SHA) — satisfied.
- AC2 (`.github/dependabot.yml` for weekly github-actions) — satisfied.
- AC3 (PR required checks green) — satisfied.
- AC4 (post-merge `master` deterministic run green) — satisfied.
```

- [ ] **Step 5: Verify spec evidence section exists and contains AC mapping**

Run:
```bash
grep -n "## Supply-Chain Hardening Evidence (Step 7)" docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md
grep -n "AC1 (\`uses\` pinned to immutable SHA)" docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md
grep -n "AC4 (post-merge \`master\` deterministic run green)" docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md
```

Expected: all three checks match.

- [ ] **Step 6: Commit Task 3**

```bash
git add docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md
git commit -m "docs(ci): record supply-chain hardening evidence"
```

---

## Final Verification Gate

- [ ] Run:
```bash
git status --short
gh pr checks <pr-number> --repo SyrexBlack/Vitbon_kassa_high
gh run list --workflow "CI Deterministic Gate" --repo SyrexBlack/Vitbon_kassa_high --branch master --limit 1
```
Expected: clean working tree and latest validation evidence still green.

- [ ] Close Beads task after evidence is committed and merged:
```bash
bd close vitbon-kassa-ngn --reason "SHA pinning + Dependabot enabled, PR checks green, post-merge deterministic run green, evidence documented"
```
