# CHOIR-87 — Validate movements against formation constraints

## Scope

Validate proposed draft-position replacements against:
1. Locked positions.
2. Proximity constraints.
3. Mobility constraints.

Visibility is explicitly out of scope for this first implementation. Do not create a VisibilityConstraintValidator or modify visibility services, evaluators, or roster handling.

Frontend feedback belongs to CHOIR-113 and is out of scope.

## Transactional requirements

All reads, locking, validation, and writes must remain inside the existing R2DBC transaction used by:

`FormationCatalogPersistenceAdapter.replaceDraftPositions()`

Do not introduce another transaction manager, blocking calls, or independent application-service evaluations in the draft-save flow.

Preserve the existing transaction boundary to prevent time-of-check/time-of-use inconsistencies.

## Validation sequence

1. Resolve the formation and occurrence.
2. Lock the formation version using the existing `lockVersion(...)` mechanism.
3. Verify that the version is in DRAFT state.
4. Build candidate positions and required snapshots.
5. Load current positions, position locks, and active proximity and mobility constraints.
6. Execute all applicable validators and accumulate violations.
7. If violations exist, throw a structured exception without calling `replacePositions()`.
8. If validation succeeds, persist the complete replacement within the same transaction.

## Domain design

Implement pure domain validation components under `com.rubengarcia.choir.formation.domain`:

- `FormationConstraintViolation`: stable violation code, relevant identifiers, and readable message.
- `FormationConstraintValidationResult`: immutable violation collection and persistence-eligibility check.
- `FormationConstraintValidator`: pure validation contract.
- `CompositeFormationConstraintValidator`: executes all validators and accumulates violations in deterministic order.
- `LockedPositionValidator`: extracts the existing `preservePositionLocks()` rule. Preserve current coordinate, label, and deletion semantics.
- `ProximityConstraintValidator`: extracts the existing `validateProximityConstraints()` rule. Preserve Euclidean distance calculation, inclusive maximum-distance boundary, and current behavior when a position is missing.
- `MobilityConstraintValidator`: reuse the existing pure `MobilityEvaluator` and translate its findings into structured violations.

Do not move SQL, repositories, persistence entities, HTTP types, or adapter dependencies into the domain.

## Mobility policy

For an active mobility constraint:
- A satisfied evaluation allows the replacement.
- A violated evaluation rejects the replacement.
- `NOT_EVALUABLE` rejects the replacement.

Missing, ambiguous, or incomplete draft-position data must never be interpreted as satisfying a mobility constraint. Return a structured violation identifying missing or insufficient data where possible.

Do not invoke `FormationMobilityEvaluationService.evaluate()` from the transactional write flow. Reuse the pure evaluator with data loaded inside the existing transaction.

## Application exception and HTTP response

Create `com.rubengarcia.choir.formation.application.exception.FormationConstraintViolationException`.

Update `FormationPlanningExceptionHandler` to map this exception to HTTP 409 with a `ProblemDetail` response containing a `violations` collection.

Each violation should expose only:
- Stable violation code.
- Relevant identifiers when available.
- Readable message.

Do not expose internal exception details or persistence information.

## Expected files

### Modify
- `src/main/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationCatalogPersistenceAdapter.java`
- `src/main/java/com/rubengarcia/choir/formation/adapter/in/web/FormationPlanningExceptionHandler.java`
- `src/test/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationCatalogPersistenceAdapterTests.java`

### Create
- `src/main/java/com/rubengarcia/choir/formation/domain/FormationConstraintViolation.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/FormationConstraintValidationResult.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/FormationConstraintValidator.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/CompositeFormationConstraintValidator.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/LockedPositionValidator.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/ProximityConstraintValidator.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/MobilityConstraintValidator.java`
- `src/main/java/com/rubengarcia/choir/formation/application/exception/FormationConstraintViolationException.java`
- Unit tests for the domain validators and composite.
- `src/test/java/com/rubengarcia/choir/formation/adapter/in/web/FormationPlanningExceptionHandlerTests.java`

Confirm auxiliary immutable input/context types and required port changes against the actual code before introducing them. Keep them within formation domain/application boundaries.

## Required tests

### Locked positions
- Unchanged locked coordinates are accepted.
- Changing locked coordinates is rejected.
- Existing label and deletion semantics remain unchanged.

### Proximity
- Positions within maximum distance are accepted.
- The exact maximum-distance boundary is accepted.
- Positions beyond maximum distance are rejected.
- Missing-position behavior remains compatible with the existing implementation.

### Mobility
- Satisfied mobility evaluations are accepted.
- Violated mobility evaluations are rejected.
- Missing, ambiguous, or incomplete draft-position data produces NOT_EVALUABLE and rejects persistence.

### Composite validation
- All validators execute.
- Multiple violations are accumulated.
- Violation ordering is deterministic.

### Persistence and transaction
- A valid replacement persists the complete position set.
- A rejected replacement does not modify positions, snapshots, or locks.
- Persistence failures roll back the complete replacement.

### HTTP
- Constraint violations return HTTP 409.
- The response exposes structured violations without internal implementation details.

Run focused tests, exception-handler tests, and the complete backend test suite.

## Explicit exclusions
- No visibility validation in CHOIR-87.
- No Angular/frontend changes.
- No changes to transition behavior or CHOIR-121/CHOIR-122.
- No independent mobility or visibility service calls from the transactional save flow.
