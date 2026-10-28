# Transition planning: COUNT group assignments

## Endpoint

POST /api/choirs/{choirId}/transitions/{transitionId}/versions/{transitionVersionId}/rule-evaluations

The request body is optional. The assignments property supplies explicit singer membership for COUNT groups used during transition planning evaluation.

## Request example

{
  "assignments": [
    {
      "ruleRevisionId": 101,
      "groupOrder": 0,
      "singerIds": [11, 12, 13, 14]
    },
    {
      "ruleRevisionId": 101,
      "groupOrder": 1,
      "singerIds": [21, 22, 23, 24]
    }
  ]
}

## Assignment contract

- ruleRevisionId identifies the exact scene-rule revision.
- groupOrder identifies a group within that revision.
- singerIds explicitly lists the group's singers.
- A COUNT group requires exactly memberCount distinct, positive singer IDs.
- The same groupOrder in different revisions identifies different groups.
- Membership must never be inferred from vocal sections, divisions, list order, or formation size.
- IDENTIFIED groups continue using the singer IDs stored in their revision.
- Missing or incomplete COUNT assignments produce NOT_EVALUABLE with COUNT_GROUP_ASSIGNMENT_UNAVAILABLE.
- Duplicate assignments for the same revision and group must not be resolved by arbitrarily choosing one.
- Transition-version authorization remains required.

## Determinism

Identical formation snapshots, rule revisions, planning parameters, and explicit assignments must produce the same rule outcomes.

This contract defines participant membership only. It does not duplicate or redefine spatial relationships, separation thresholds, movement rules, or other transition calculations owned by the existing domain logic.
