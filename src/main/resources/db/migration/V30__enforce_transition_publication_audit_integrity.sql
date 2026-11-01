ALTER TABLE transition_rule_evaluations
    ADD CONSTRAINT uq_transition_rule_evaluation_version
    UNIQUE (id, transition_version_id);

ALTER TABLE transition_rule_evaluations
    ADD CONSTRAINT uq_transition_rule_evaluation_snapshot
    UNIQUE (
        id,
        transition_version_id,
        transition_id,
        from_formation_version_id,
        to_formation_version_id
    );

ALTER TABLE transition_rule_evaluations
    ADD CONSTRAINT fk_transition_rule_evaluation_version_snapshot
    FOREIGN KEY (
        transition_version_id,
        transition_id,
        from_formation_version_id,
        to_formation_version_id
    )
    REFERENCES transition_versions (
        id,
        transition_id,
        from_formation_version_id,
        to_formation_version_id
    );

ALTER TABLE transition_publication_evidence
    ADD CONSTRAINT fk_transition_publication_evidence_evaluation_snapshot
    FOREIGN KEY (
        evaluation_id,
        transition_version_id,
        transition_id,
        from_formation_version_id,
        to_formation_version_id
    )
    REFERENCES transition_rule_evaluations (
        id,
        transition_version_id,
        transition_id,
        from_formation_version_id,
        to_formation_version_id
    );

ALTER TABLE transition_publication_decisions
    ADD CONSTRAINT fk_transition_publication_decision_evaluation_version
    FOREIGN KEY (evaluation_id, transition_version_id)
    REFERENCES transition_rule_evaluations (id, transition_version_id);
