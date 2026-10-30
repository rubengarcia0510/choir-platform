ALTER TABLE transition_versions
    ADD CONSTRAINT uq_transition_version_publication_snapshot
    UNIQUE (
        id,
        transition_id,
        from_formation_version_id,
        to_formation_version_id
    );

ALTER TABLE transition_publication_evidence
    ADD CONSTRAINT fk_transition_publication_version_snapshot
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
