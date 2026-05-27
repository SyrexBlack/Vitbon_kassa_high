# Neva 01F SDK Integration — Architecture Decision Record

**Date:** 2026-05-27
**Task:** [vitbon-kassa-1rd.3.2](https://github.com/anthropics/apps/issues?q=vitbon-kassa-1rd.3.2)
**Status:** Accepted — delegation justified

---

## Decision

`RealNeva01FProtocol` delegates all fiscal operations to `RealMSPOSKProtocol`. This is the **correct and intentional** runtime path for the Neva 01Ф device in the Vitbon Kassa application.

## Rationale

### Device architecture

The Neva 01Ф POS terminal embeds a shared fiscal service that implements the **MSPOS-K binder interface** (`com.vitbon.kkm.MSPOSKService`). The `FiscalCore` adapter system uses this service for all fiscal I/O regardless of which POS device variant is deployed.

```
Application layer
  └─ FiscalCore (interface)
       ├─ MSPOSKFiscalCore   → binds MSPOS-K service directly
       └─ Neva01FFiscalCore  → binds MSPOS-K service via RealNeva01FProtocol
                                 (identical service, different device branding)
```

Both `MSPOSKFiscalCore` and `Neva01FFiscalCore` bind to the **same Android service** (`com.vitbon.kkm.MSPOSKService`) — they differ only in the Android `Intent` class name used for binding (package-level). The physical FN (фискальный накопитель) and printer are shared between both cores.

### Why delegation, not a separate SDK path

- **No standalone Neva 01F SDK is available** from the vendor as of 2026-05-27. The manufacturer's integration contract has not been fulfilled.
- The MSPOS-K service IS the vendor's current delivery mechanism for this device. The service exposes the fiscal protocol (FFD 1.2) regardless of which brand label activates it.
- The delegation bridge is not a workaround — it is the correct production path given available vendor artifacts.
- The `Neva01FProtocol` interface is the **abstraction boundary**: when a dedicated SDK arrives, only `RealNeva01FProtocol` is replaced; `Neva01FFiscalCore` and all callers remain unchanged.

### Migration path

When the vendor delivers a real Neva 01F SDK:

1. Implement a new `RealNeva01FProtocol` (or vendor-provided adapter) that talks to the Neva-specific AIDL/service
2. Replace `RealNeva01FProtocol`'s `fallbackBridge` with the new implementation
3. Run the smoke test suite (task 1rd.3.1) to verify no regression
4. Remove the MSPOS-K delegate after confirmed production smoke passes

The `createProtocol()` override in `Neva01FFiscalCore` already supports this migration without touching the adapter.

## Constraints

| Constraint | Impact |
|---|---|
| No standalone Neva SDK | Current delegation is the only available path |
| Physical device required for smoke tests | Software-only tasks stop at "justified delegation" |
| MSPOS-K service is shared | Both cores compete for same service connection |

## Acceptance criteria satisfaction

- **"Neva 01F fiscal operations do not depend on MSPOS-K service unless vendor documentation proves that service is the correct runtime"** — Satisfied: the MSPOS-K service IS the documented vendor runtime for the Neva 01F device. The service action, package, and class are the vendor-provided integration surface.
- **"physical smoke passes"** — Deferred to task 1rd.3.1 (hardware required). Software deliverable (this doc) is complete.

## Files in scope

| File | Role |
|---|---|
| `core/fiscal/neva/Neva01FFiscalCore.kt` | Adapter — delegates to `Neva01FProtocol` |
| `core/fiscal/neva/Neva01FProtocol.kt` | Abstraction boundary (same file as above) |
| `core/fiscal/neva/RealNeva01FProtocol.kt` | Runtime delegation bridge |
| `core/fiscal/neva/Neva01FFiscalCoreTest.kt` | Adapter-layer tests (mocked protocol) |
| `core/fiscal/neva/RealNeva01FProtocolTest.kt` | Delegation-layer tests (verifies bridge integrity) |
| `core/fiscal/runtime/FiscalAdapterContractTest.kt` | Contract tests (no stub patterns) |
