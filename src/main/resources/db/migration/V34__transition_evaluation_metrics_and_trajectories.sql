CREATE TABLE transition_rule_evaluation_displacement_metrics (
    evaluation_id BIGINT PRIMARY KEY
        REFERENCES transition_rule_evaluations(id),
    total_distance DECIMAL(20, 6) NOT NULL,
    max_individual_distance DECIMAL(20, 6) NOT NULL,
    moving_singer_count INTEGER NOT NULL,
    CONSTRAINT ck_transition_displacement_metrics_nonnegative
        CHECK (
            total_distance >= 0
            AND max_individual_distance >= 0
            AND moving_singer_count >= 0
        )
);

CREATE TABLE transition_rule_evaluation_singer_distances (
    evaluation_id BIGINT NOT NULL
        REFERENCES transition_rule_evaluations(id),
    singer_id BIGINT NOT NULL,
    distance DECIMAL(20, 6) NOT NULL,
    CONSTRAINT pk_transition_evaluation_singer_distance
        PRIMARY KEY (evaluation_id, singer_id),
    CONSTRAINT ck_transition_evaluation_singer_distance_nonnegative
        CHECK (distance >= 0)
);

CREATE TABLE transition_rule_evaluation_displacement_issues (
    evaluation_id BIGINT NOT NULL
        REFERENCES transition_rule_evaluations(id),
    issue_order INTEGER NOT NULL,
    issue_code VARCHAR(60) NOT NULL,
    stage VARCHAR(5) NOT NULL,
    singer_id BIGINT,
    CONSTRAINT pk_transition_evaluation_displacement_issue
        PRIMARY KEY (evaluation_id, issue_order),
    CONSTRAINT ck_transition_displacement_issue_order
        CHECK (issue_order >= 0),
    CONSTRAINT ck_transition_displacement_issue_stage
        CHECK (stage IN ('FROM', 'TO'))
);

CREATE TABLE transition_rule_evaluation_trajectory_analyses (
    evaluation_id BIGINT PRIMARY KEY
        REFERENCES transition_rule_evaluations(id),
    transition_version_id BIGINT NOT NULL
        REFERENCES transition_versions(id)
);

CREATE TABLE transition_rule_evaluation_trajectory_crossings (
    evaluation_id BIGINT NOT NULL
        REFERENCES transition_rule_evaluation_trajectory_analyses(evaluation_id),
    first_singer_id BIGINT NOT NULL,
    second_singer_id BIGINT NOT NULL,
    temporal_conflict_status VARCHAR(30) NOT NULL,
    CONSTRAINT pk_transition_evaluation_trajectory_crossing
        PRIMARY KEY (evaluation_id, first_singer_id, second_singer_id),
    CONSTRAINT ck_transition_trajectory_crossing_order
        CHECK (first_singer_id < second_singer_id),
    CONSTRAINT ck_transition_trajectory_conflict_status
        CHECK (temporal_conflict_status IN (
            'CONFIRMED', 'NOT_CONCURRENT', 'NOT_EVALUABLE'
        ))
);

CREATE TABLE transition_rule_evaluation_trajectory_issues (
    evaluation_id BIGINT NOT NULL
        REFERENCES transition_rule_evaluation_trajectory_analyses(evaluation_id),
    issue_order INTEGER NOT NULL,
    issue_code VARCHAR(60) NOT NULL,
    stage VARCHAR(5) NOT NULL,
    singer_id BIGINT,
    CONSTRAINT pk_transition_evaluation_trajectory_issue
        PRIMARY KEY (evaluation_id, issue_order),
    CONSTRAINT ck_transition_trajectory_issue_order
        CHECK (issue_order >= 0),
    CONSTRAINT ck_transition_trajectory_issue_stage
        CHECK (stage IN ('FROM', 'TO'))
);

CREATE INDEX idx_transition_trajectory_crossings_singers
    ON transition_rule_evaluation_trajectory_crossings
        (first_singer_id, second_singer_id);
