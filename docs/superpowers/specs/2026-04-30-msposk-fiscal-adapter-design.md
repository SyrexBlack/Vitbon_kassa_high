# MSPOS-K Fiscal Adapter Production Design

Date: 2026-04-30
Issue: vitbon-kassa-65j
Scope: Bring the MSPOS-K fiscal adapter to production-ready runtime behavior through the real vendor service path, without broad fiscal-architecture refactoring.

## Objective

Clear the MSPOS-K P0 blocker by hardening the existing adapter so fiscal operations execute through the real vendor runtime with deterministic success, timeout, and error semantics.

## Context

The current codebase already contains a substantial MSPOS-K binder/service bridge in [android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt](android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt). Consequently, the central problem is not SDK scaffolding from zero, but validating and completing the existing runtime path.

Repo docs establish the production target clearly:
- real fiscal operations must run on MSPOS-K hardware;
- synthetic stub behavior must not remain active in the production path;
- acceptance evidence must include smoke execution of open/sale/return/correction/cash in-out/x-report/z-report;
- adapter behavior must be deterministic under service unavailability, timeout, and vendor callback errors.

## In Scope

1. Harden [MSPOSKFiscalCore.kt](android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt) as the production MSPOS-K adapter.
2. Verify and correct `RealMSPOSKProtocol` service binding, callback bridging, and method/result mapping.
3. Remove or fence off any remaining synthetic success behavior in the MSPOS-K path.
4. Add adapter-level tests for operation mapping, timeout behavior, and error translation.
5. Add or extend smoke-verification hooks/evidence requirements for real MSPOS-K runtime checks.

## Out of Scope

1. Neva 01F production integration.
2. Shared `FiscalCore` interface redesign.
3. Broad FFD subsystem redesign outside the MSPOS-K adapter surface.
4. Release-wide final verification (`vitbon-kassa-nhy`).

## Architecture Decision

Keep the existing adapter layering intact:
- `FiscalCoreProvider` continues selecting `MSPOSKFiscalCore`.
- `MSPOSKFiscalCore` remains the upper adapter boundary used by app/domain layers.
- `RealMSPOSKProtocol` remains the vendor-runtime integration boundary.
- Binder/reflection logic stays isolated inside the MSPOS-K runtime bridge.

This approach minimizes blast radius and preserves the current wiring already used by the app. A rewrite from scratch is not justified unless the existing bridge is proven structurally incompatible with the vendor runtime, and current evidence does not show that.

## Required Runtime Behavior

For every fiscal operation exposed through `FiscalCore`, the MSPOS-K adapter must:
1. connect to the real fiscal service or fail explicitly;
2. translate domain input to the vendor method contract;
3. await vendor completion with bounded timeout semantics;
4. translate vendor callback failures into stable `FiscalException` behavior;
5. return a valid `FiscalResult.Success` only when fiscal identifiers are present and non-synthetic.

Operations covered by this requirement:
- shift open;
- sale;
- return;
- correction;
- cash in;
- cash out;
- X-report;
- shift close / Z-report;
- status;
- FFD version read.

## Error and Recovery Rules

The adapter must distinguish between recoverable and non-recoverable failures instead of collapsing everything into a generic unknown error.

Recoverable failures include:
- service not ready;
- bind timeout;
- transport timeout;
- vendor-reported retryable status.

Non-recoverable failures include:
- invalid vendor method mapping/signature mismatch;
- missing required fiscal identifiers after an operation completes;
- vendor-reported terminal failure such as not-enough-cash or unsupported operation.

Where the vendor bridge exposes callback error codes, those codes must remain the source of truth for recoverability mapping.

## Test Strategy

### 1. Adapter contract tests

Add or extend tests that prove:
- each public MSPOS-K fiscal operation delegates into the protocol boundary;
- sale and return preserve required type checks;
- success requires real fiscal identifiers rather than synthetic placeholders;
- timeout and bind-failure paths map into `FiscalException` with correct recoverability.

### 2. Protocol/transport tests

Add focused tests around the runtime bridge behavior where feasible without hardware:
- binder connection timeout;
- null binder/service disconnect behavior;
- vendor callback error mapping;
- missing vendor method signature failure.

### 3. Hardware smoke evidence

The acceptance package for this issue must cover MSPOS-K on real runtime:
- open shift;
- sale;
- return;
- correction;
- cash in/out;
- X-report;
- close shift;
- status/FFD read.

Each recorded success must preserve real fiscal identifiers and must not use synthetic prefixes such as `MSP_`.

## Implementation Decomposition

1. Inventory the current MSPOS-K adapter surface by operation and classify each path as vendor-backed, partially synthetic, or unverifiable.
2. Add failing tests for the first observed contract gaps.
3. Apply minimal fixes inside `MSPOSKFiscalCore` / `RealMSPOSKProtocol` to satisfy those tests.
4. Tighten result validation so incomplete vendor responses fail explicitly.
5. Re-run targeted fiscal adapter tests and then the broader Android unit/build verification required by the phase.

## Definition of Done

This issue is done only when all of the following are true:
1. MSPOS-K fiscal operations use the real vendor service path rather than a synthetic production path.
2. Adapter tests cover success, timeout/bind failure, and vendor error translation for the MSPOS-K surface.
3. The adapter returns success only with valid fiscal identifiers.
4. No MSPOS-K production path emits synthetic fiscal-sign placeholders.
5. Verification evidence exists for both code-level tests and MSPOS-K runtime smoke checks.

## Risks

1. Vendor runtime signatures may differ across devices or SDK revisions.
   - Mitigation: keep reflection isolated and fail explicitly on signature mismatch.
2. Existing code may partially succeed while still returning incomplete fiscal metadata.
   - Mitigation: make fiscal identifiers part of success validation.
3. Over-coupling MSPOS-K work to Neva behavior could expand scope.
   - Mitigation: keep this issue MSPOS-K-only and treat Neva separately.

## Immediate Next Step

Write the implementation plan for `vitbon-kassa-65j` as a narrow sequence of TDD-backed adapter hardening tasks, then execute the first ready task in isolation.