# Changelog

All notable changes to this project are documented in this file.

## [0.3.0] - 2026-11-25

### Added

- **Formation position locks:** preserve the coordinates of locked singers while they remain in a proposed formation.
- **Proximity constraints:** define maximum distances between singers and validate proposed positions against those limits.
- **Mobility constraints:** define allowed rectangular regions for singers, using the formation's normalized coordinate system.
- **Visibility constraints:** register singers whose visibility should be protected, and provide application-level geometric evaluation using position, depth, and height snapshots.
- **Constraint management APIs:** expose REST operations to manage proximity, mobility, and visibility constraints.
- **Singer height snapshots:** persist height values used by formation planning and visibility evaluation.
- **Composite movement validation:** validate proposed draft-position replacements against position locks, proximity constraints, and mobility constraints.
- **Structured validation errors:** return HTTP 409 responses with machine-readable violation details when a proposed replacement violates constraints.

### Database

- Add migrations V37–V41 for proximity constraints, singer height snapshots, physical constraints, mobility uniqueness, and mobility coordinate bounds.

### Tests and validation

- Add domain, application, persistence, controller, authorization, transaction rollback, and architecture tests for the constraint capabilities.
- Validate the release candidate with the H2 test suite and PostgreSQL 16 CI workflow.

### Known limitations

- Visibility evaluation is available through the application service, but is not yet exposed through a dedicated HTTP evaluation endpoint.
- Composite draft-movement validation currently includes position locks, proximity, and mobility; visibility evaluation is not yet part of that composite.
- Visibility evaluation is an approximation based on the available two-dimensional coordinates, depth, and singer height. It is not a full three-dimensional optical line-of-sight simulation.
