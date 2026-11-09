CREATE TABLE transition_movement_steps (
    transition_version_id BIGINT NOT NULL,
    transition_id BIGINT NOT NULL,
    from_formation_version_id BIGINT NOT NULL,
    to_formation_version_id BIGINT NOT NULL,
    choir_id BIGINT NOT NULL,
    singer_id BIGINT NOT NULL,
    sequence_number INTEGER NOT NULL,
    temporal_reference TIMESTAMP WITH TIME ZONE NOT NULL,
    action VARCHAR(100) NOT NULL,
    CONSTRAINT pk_transition_movement_steps
        PRIMARY KEY (transition_version_id, sequence_number, singer_id),
    CONSTRAINT fk_transition_movement_step_version
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
        ),
    CONSTRAINT fk_transition_movement_step_choir
        FOREIGN KEY (transition_id, choir_id)
        REFERENCES transitions (id, choir_id),
    CONSTRAINT ck_transition_movement_step_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT ck_transition_movement_step_action
        CHECK (CHAR_LENGTH(TRIM(action)) > 0)
);

CREATE INDEX idx_transition_movement_steps_version_time
    ON transition_movement_steps (transition_version_id, temporal_reference);

CREATE TABLE transition_rule_evaluation_movement_steps (
    evaluation_id BIGINT NOT NULL,
    transition_version_id BIGINT NOT NULL,
    transition_id BIGINT NOT NULL,
    from_formation_version_id BIGINT NOT NULL,
    to_formation_version_id BIGINT NOT NULL,
    singer_id BIGINT NOT NULL,
    sequence_number INTEGER NOT NULL,
    temporal_reference TIMESTAMP WITH TIME ZONE NOT NULL,
    action VARCHAR(100) NOT NULL,
    CONSTRAINT pk_transition_rule_evaluation_movement_steps
        PRIMARY KEY (evaluation_id, sequence_number, singer_id),
    CONSTRAINT fk_transition_eval_movement_snapshot
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
        ),
    CONSTRAINT ck_transition_eval_movement_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT ck_transition_eval_movement_action
        CHECK (CHAR_LENGTH(TRIM(action)) > 0)
);

CREATE INDEX idx_transition_eval_movement_evaluation_sequence
    ON transition_rule_evaluation_movement_steps (evaluation_id, sequence_number);
