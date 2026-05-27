# Backend Monitoring Reference

## Health Endpoints

| Endpoint | Status | Auth | Use |
|----------|--------|------|-----|
| `GET /api/v1/health` | UP | none | Basic liveness |
| `GET /api/v1/health/live` | ALIVE | none | K8s liveness probe |
| `GET /api/v1/health/ready` | READY | none | K8s readiness probe |
| `GET /api/v1/auth/login` | — | none | Auth start |
| `GET /api/v1/license/check?deviceId=` | — | none | License validation |

## Rollback Procedure

1. **Database**: Flyway migrations are ordinal. To roll back N versions:
   ```sql
   -- Check current version
   SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;

   -- Revert to V6 (example)
   UPDATE flyway_schema_history SET installed_on = NULL, success = 0 WHERE installed_rank > 6;
   ```
2. **Backend JAR**: Keep previous `-NNN` artifact archived. Swap image/tag.
3. **DDL rollback**: Re-run previous migration SQL manually if Flyway can't auto-revert.
4. **Verification**: `GET /api/v1/health` returns UP before traffic.

## Flyway Dry Run

Dry run is not built-in Flyway but can be tested with a staging clone:
```bash
# Point a staging DB to the migrations before applying:
export DB_URL=jdbc:postgresql://staging-pg:5432/vitbon_staging
./gradlew :backend:flywayMigrate -i
# Review output, then revert staging if errors
```

## Integration Monitors

- **Auth**: `POST /api/v1/auth/login` → 200 with session token
- **License**: `GET /api/v1/license/check?deviceId=` → 200 ok / 403 block
- **Sync down**: `GET /api/v1/checks` → 200 (requires auth)
- **EGAIS**: `GET /api/v1/egais/info` → returns UTM status
- **Audit trail**: `GET /api/v1/audit/` → returns entries (admin role)
