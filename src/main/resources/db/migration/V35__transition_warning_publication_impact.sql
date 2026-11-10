ALTER TABLE transition_rule_evaluation_warnings
    ADD COLUMN publication_impact VARCHAR(20) NOT NULL DEFAULT 'BLOCKING';

ALTER TABLE transition_rule_evaluation_warnings
    ADD CONSTRAINT ck_transition_rule_warning_publication_impact
        CHECK (publication_impact IN ('BLOCKING', 'INFORMATIONAL'));
