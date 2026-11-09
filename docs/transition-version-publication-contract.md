# TransitionVersion publication contract (CHOIR-97 / CHOIR-94)

## Entry point and ownership

Publication is an operation on the existing TransitionVersion lifecycle, exposed
through TransitionVersionController and coordinated by
TransitionVersionLifecycleService. It must not introduce a second publication
flow or confuse transition versions with FormationVersion publication.

The authenticated director identity comes from the security context, never
from the request body. The application service authorizes choir ownership
before invoking the persistence port.

## Request intent

A publication request identifies one persisted rule evaluation by ID. It may
include explicit acceptances for ordinary violated warnings. Each acceptance
identifies the exact warning by evaluation ID, result order and warning order,
and includes a non-blank reason. A global warning uses result order -1.
The request cannot override the evaluation status or provide a replacement result.

## Publication guards

The persistence adapter must lock the transition/version and perform all checks
and writes in one reactive transaction:

1. The transition belongs to the requested choir and the version belongs to
   that transition and remains DRAFT.
2. The evaluation exists and belongs to this exact transition version; its
   FROM/TO formation version IDs match the version.
3. NOT_EVALUABLE always blocks publication.
4. Any NOT_EVALUABLE warning blocks publication, including director visibility.
   Director visibility is never eligible for acceptance because the relative
   geometry model cannot establish physical visibility.
5. An ordinary VIOLATED warning may be accepted only when the request identifies
   that exact warning. The original evaluation/result remains VIOLATED.
6. Every unaccepted ordinary violation blocks publication. Unknown, satisfied,
   duplicated, or non-violated warning references are rejected.
7. Accepted decisions, immutable publication evidence and the DRAFT-to-PUBLISHED
   update happen in one transaction. Any failure rolls back all writes and leaves
   the version in DRAFT.
8. A version can be published only once. Published evidence, decisions and
   evaluation history are never updated or reconstructed from current rules/formations.

## Audit model

transition_publication_evidence binds the published transition version to the
exact evaluation and copies the transition ID, FROM/TO formation version IDs,
separation settings revision and threshold, publisher identity and publication
timestamp.

transition_publication_decisions records each accepted warning separately,
including its original location, code, explanation, director, reason and timestamp.
A global warning uses result_order = -1.

This migration adds audit tables without modifying previously applied migrations.
SQL, locking, transaction coordination and row mapping belong exclusively in the
persistence adapter. The application service coordinates authorization and
delegates the atomic publication operation through a port.

## Temporal movement evidence (CHOIR-120)

Expected movement sequences are part of each immutable scene-rule revision.
For a `MOVEMENT` rule with `orderingMode: "SEQUENTIAL"`, every expected step
has a 1-based `order`, one or more `participants` using the existing `SINGER` or
`GROUP` references, and a non-blank `action`.

Observed movement steps are replaced on a transition draft through
`PUT /api/choirs/{choirId}/transitions/{transitionId}/versions/{transitionVersionId}/movement-steps`
and read through `GET` on the same path. Each item identifies a singer,
1-based sequence number, ISO-8601 `temporalReference` with an explicit offset
(normalized to UTC by the application), and action. The version's transition and exact FROM/TO snapshots are
resolved by the server; request data cannot associate a step with another
transition. The adapter locks the version, verifies its DRAFT state and singer
ownership, and replaces the complete list transactionally. An empty list clears
the draft's capture.

Evaluation reads the captured steps for that exact version and copies them into
`transition_rule_evaluation_movement_steps` in the same transaction as the
evaluation, results, and warnings. History reads return this original evidence;
replacing draft steps never updates an earlier evaluation. Publication compares
the selected evaluation's movement evidence with the current draft capture
while holding the transition-version lock. It rejects stale evaluations, and
the DRAFT-only capture operation prevents any changes after publication.

All participants assigned to one observed sequence number must have the same
temporal reference, and references must increase strictly between sequence
numbers. Missing or incomplete expected/observed data, duplicate participants,
ambiguous ordering, unknown participants, or version mismatches produce
`NOT_EVALUABLE`. A complete sequence with the wrong order, participants, or
actions produces `VIOLATED`; action comparison is case-insensitive after
trimming. Static FROM/TO positions are never used as temporal evidence; they
remain available only to spatial constraints.

## Required tests

- Capture, replace, and retrieve temporal movement steps only on the owned DRAFT
  transition version; verify the exact FROM/TO and choir association.
- Evaluate a sequential movement rule, verify its result and persisted temporal
  evidence, then replace draft steps and verify earlier evaluation evidence is
  unchanged.
- Reject publication when captured movement evidence changed after evaluation,
  and reject movement capture after publication.
- Inject a movement-evidence persistence failure and verify the evaluation and
  its child rows roll back together.
- Run the movement migration and integration coverage on H2 and PostgreSQL.
- Publish a satisfied evaluation without acceptances.
- Reject missing/foreign evaluations and evaluations belonging to another version.
- Reject NOT_EVALUABLE evaluations or warnings, including visibility.
- Reject an ordinary violation without explicit acceptance.
- Reject acceptance of unknown or non-violated warnings.
- Publish an ordinary violation only with a reasoned acceptance of the exact warning.
- Verify evidence and decisions refer to the exact evaluation and snapshots.
- Inject a persistence failure and verify rollback leaves the version DRAFT with
  no decision/evidence rows.
- Reject repeated publication and verify historical data remains unchanged.
