ALTER TABLE transition_rule_evaluation_warnings
    ADD COLUMN result_order_key INTEGER
    GENERATED ALWAYS AS (COALESCE(result_order, -1)) STORED;

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
