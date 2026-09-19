## Summary

<!-- What changes and why, in one or two sentences. -->

## Work package and requirements

- Work package: <!-- e.g. M1-B2 -->
- Requirements: <!-- e.g. FR-ADM-01, NFR-09 -->

## How to verify

<!-- Commands, endpoints, or screens a reviewer can use to confirm the change. -->

## Checklist

- [ ] Tests written first and passing (TDD)
- [ ] `docs/tech-spec.md` updated if the implementation deviates from it
- [ ] `backend/api/openapi.json` and frontend API types regenerated if the API changed
- [ ] New error codes, metrics, and i18n IDs follow the tech-spec conventions
- [ ] No secrets, credentials, or personal data in code, config, or logs
- [ ] Work package status updated in `docs/implementation-plan.md`

### Release PRs to `main` only

- [ ] OWASP Top 10 checklist reviewed (NFR-09)
- [ ] Migrations are expand/contract only (safe to roll back)
