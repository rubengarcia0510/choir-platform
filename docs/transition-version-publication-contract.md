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

## Required tests

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
