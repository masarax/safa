# SAFA web asset contract

SAFA intentionally uses a zero-build production web frontend. The authoritative browser assets are committed under `backend/public/` and are the exact files validated by backend CI and uploaded by the production deployment workflow.

## Canonical production assets

- `backend/public/safa-web.css`
- `backend/public/safa-web.js`
- `backend/public/safa-web-product.js`
- related committed images/icons/fonts under `backend/public/`

Laravel Blade templates reference those same-origin public files directly. There is no Vite, Tailwind, npm, Node or generated-manifest production path in this repository. Do not add a second frontend build graph unless the application is deliberately migrated in one reviewed change with locked dependencies, CI build output, CSP compatibility and deployment provenance.

## Change process

1. Edit the canonical `backend/public/safa-web*` source file.
2. Keep JavaScript compatible with the repository's strict CSP: do not introduce inline/eval-based execution.
3. Run backend CI; it syntax-checks the exact JavaScript that production will serve and runs the web/Laravel regression suite.
4. Deploy only through the normal immutable backend deployment flow. Deployment uploads the same checked-in public assets that CI validated.

Browser cache invalidation is release-owned. When a long-lived cache policy is introduced, asset URLs must become content/release-addressed before immutable caching is enabled; do not mark mutable filenames immutable.
