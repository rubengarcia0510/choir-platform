# CHOIR-87 — Validate movements against formation constraints

## Scope

Validate proposed draft-position replacements against:
1. Locked positions.
2. Proximity constraints.
3. Mobility constraints.

Visibility is explicitly out of scope. Do not create a VisibilityConstraintValidator or modify visibility services, evaluators, or roster handling.

Frontend feedback belongs to CHOIR-113.

## Existing implementation verified on the base branch

### Draft replacement transaction

`FormationCatalogPersistenceAdapter.replaceDraftPositions()` is the existing transactional entry point. Keep all reads, locking, validation, and writes inside its current R2DBC transaction.

Preserve the existing sequence:
- Resolve formation and occurrence.
- Lock the version with `lockVersion(...)`.
- Verify DRAFT status.
- Build candidate positions and snapshots.
- Validate constraints.
- Persist only after all validations succeed.

Do not introduce another transaction manager, blocking calls, or independent application-service evaluations in the write flow.

### Locked positions

Existing method: `preservePositionLocks(...)` in `FormationCatalogPersistenceAdapter`.

Current behavior:
- Loads existing positions for the version.
- Matches current and replacement positions by singer ID.
- For an existing locked position, rejects changes to X or Y coordinates.
- Preserves `locked=true` on the replacement when coordinates are unchanged.
- Does not independently reject missing locked singers or compare labels.

Extract this behavior without changing its semantics. Any change to deletion or label behavior requires separate explicit requirements and tests; do not invent new rules.

### Proximity constraints

Existing method: `validateProximityConstraints(...)` in `FormationCatalogPersistenceAdapter`.

Current behavior:
- Reads active rows from `formation_proximity_constraints`.
- Builds a candidate-position lookup by singer ID.
- Calculates squared Euclidean distance using `BigDecimal`.
- Rejects only when distance squared is greater than maximum distance squared.
- Therefore the exact maximum-distance boundary is accepted.
- If either singer is absent from the candidate positions, the current implementation continues without rejection.

Preserve these rules exactly when extracting the validator.

The existing `FormationProximityConstraintPersistenceAdapter` and `FormationProximityConstraintPort` expose `findForFormation(...)`, but their normal application-service path must not be called from the transactional write flow if it would execute outside the current transaction.

### Mobility constraints

Existing components:
- `FormationMobilityConstraintPersistenceAdapter`
- `FormationMobilityConstraintPort`
- `FormationMobilityConstraint`
- `MobilityRegion`
- `MobilityEvaluator`
- `MobilityEvaluation`
- `MobilityEvaluationStatus`
- `MobilityFinding`
- `MobilityNotEvaluable`

The persistence adapter already has a `findForFormation(...)` query for mobility constraints. However, `replaceDraftPositions()` does not currently load or evaluate mobility constraints.

Load the active mobility constraints using the existing transaction-bound `DatabaseClient` or an equivalent mechanism proven to participate in the same R2DBC transaction. Do not call `FormationMobilityEvaluationService.evaluate()` from the write flow.

Reuse the pure `MobilityEvaluator` with the candidate positions and loaded constraints.

For active mobility constraints:
- Satisfied evaluation allows persistence.
- Violated evaluation rejects persistence.
- NOT_EVALUABLE rejects persistence.

Missing, ambiguous, or incomplete candidate-position data must not be interpreted as satisfying a mobility constraint. Include the reason and relevant identifiers in the structured violation when available.

## Validation architecture

Keep pure validation logic under `com.rubengarcia.choir.formation.domain`.

Proposed components:
- `FormationConstraintViolation`: stable code, relevant identifiers, readable message.
- `FormationConstraintValidationResult`: immutable violation list and persistence-eligibility check.
- `FormationConstraintValidator`: pure validation contract.
- `CompositeFormationConstraintValidator`: executes all validators and accumulates violations in deterministic order.
- `LockedPositionValidator`: extracts the existing locked-position behavior.
- `ProximityConstraintValidator`: extracts the existing proximity behavior.
- `MobilityConstraintValidator`: adapts `MobilityEvaluator` results into structured violations.

Define any immutable validation context/input types only after inspecting the actual domain types. Do not put SQL, repositories, persistence entities, HTTP types, or adapter dependencies in the domain.

Avoid unnecessary abstractions if the actual types make a simpler design clearer. Keep the implementation cohesive and aligned with existing conventions.

## Persistence integration

Modify `FormationCatalogPersistenceAdapter.replaceDraftPositions()` so that:
1. Candidate positions are built after the existing lock and DRAFT checks.
2. Existing positions and active constraints are loaded inside the same transaction.
3. All three validators execute and accumulate violations.
4. A structured exception is raised when violations exist.
5. `replacePositions()` is never called when validation fails.
6. A valid replacement persists the complete candidate set using the existing transaction.

Do not duplicate existing constraint queries through application services. Prefer transaction-bound reads within the adapter where necessary to preserve atomicity.

## Exception and HTTP response

Create `FormationConstraintViolationException` under `formation.application.exception`.

Update `FormationPlanningExceptionHandler` to map this exception to HTTP 409 with `ProblemDetail` containing a `violations` collection.

Expose only:
- Stable violation code.
- Relevant identifiers when available.
- Readable message.

Do not expose internal exception details or persistence information.

Preserve existing mappings, including `PhysicalConstraintConflictException`, unless the new structured exception deliberately and compatibly replaces a specific path.

## Expected files

### Modify
- `src/main/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationCatalogPersistenceAdapter.java`
- `src/main/java/com/rubengarcia/choir/formation/adapter/in/web/FormationPlanningExceptionHandler.java`
- `src/test/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationCatalogPersistenceAdapterTests.java`
- `src/test/java/com/rubengarcia/choir/formation/adapter/in/web/FormationPlanningExceptionHandlerTests.java`

### Create
- `src/main/java/com/rubengarcia/choir/formation/domain/FormationConstraintViolation.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/FormationConstraintValidationResult.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/FormationConstraintValidator.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/CompositeFormationConstraintValidator.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/LockedPositionValidator.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/ProximityConstraintValidator.java`
- `src/main/java/com/rubengarcia/choir/formation/domain/MobilityConstraintValidator.java`
- `src/main/java/com/rubengarcia/choir/formation/application/exception/FormationConstraintViolationException.java`
- Focused unit tests for each validator and the composite.

Confirm all additional files and types against the actual code before creating them. Modify ports only if the real transactional implementation requires it and the transaction participation is demonstrated.

## Required tests

### Locked positions
- Unchanged locked coordinates are accepted.
- Changed locked coordinates are rejected.
- `locked=true` is preserved.
- Existing label and deletion behavior remains unchanged.

### Proximity
- Within maximum distance is accepted.
- Exact maximum-distance boundary is accepted.
- Beyond maximum distance is rejected.
- Missing-position behavior remains unchanged.

### Mobility
- Satisfied evaluation is accepted.
- Violated evaluation is rejected.
- NOT_EVALUABLE is rejected.
- Missing, ambiguous, or incomplete candidate data produces NOT_EVALUABLE and prevents persistence.

### Composite
- Every validator executes.
- Multiple violations are accumulated.
- Violation ordering is deterministic.

### Transaction and persistence
- Valid replacement persists the complete position set.
- Rejected replacement does not modify positions, snapshots, or locks.
- Persistence failure rolls back the complete replacement.
- Verify mobility reads participate in the same transaction as the draft replacement.

### HTTP
- Structured violations return HTTP 409.
- Response contains stable codes, relevant IDs, and readable messages.
- Internal implementation details are not exposed.
- Existing exception mappings remain compatible.

Run focused tests, exception-handler tests, and the complete backend test suite.

## Explicit exclusions

- No visibility validation.
- No Angular/frontend changes.
- No changes to transition behavior or CHOIR-121/CHOIR-122.
- No independent mobility or visibility service calls from the transactional save flow.
