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

ALTER TABLE transition_rule_evaluation_warnings
    ADD COLUMN result_order_key INTEGER;

UPDATE transition_rule_evaluation_warnings
SET result_order_key = COALESCE(result_order, -1);

ALTER TABLE transition_rule_evaluation_warnings
    ALTER COLUMN result_order_key SET NOT NULL;

ALTER TABLE transition_rule_evaluation_warnings
    ADD CONSTRAINT ck_transition_rule_warning_result_order_key
    CHECK (result_order_key = COALESCE(result_order, -1));

ALTER TABLE transition_rule_evaluation_warnings
    ADD CONSTRAINT uq_transition_rule_warning_reference
    UNIQUE (evaluation_id, result_order_key, warning_order);

ALTER TABLE transition_publication_decisions
    ADD CONSTRAINT fk_transition_publication_decision_warning
    FOREIGN KEY (evaluation_id, result_order, warning_order)
    REFERENCES transition_rule_evaluation_warnings (
        evaluation_id,
        result_order_key,
        warning_order
    );
