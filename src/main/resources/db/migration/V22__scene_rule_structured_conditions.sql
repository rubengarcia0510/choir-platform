ALTER TABLE scene_rules
    ADD COLUMN condition_json TEXT;

ALTER TABLE scene_rules
    ADD COLUMN definition_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

ALTER TABLE scene_rule_revisions
    ADD COLUMN condition_json TEXT;

ALTER TABLE scene_rule_revisions
    ADD COLUMN definition_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

ALTER TABLE scene_rules
    ADD CONSTRAINT ck_scene_rule_definition_status
    CHECK (definition_status IN ('DRAFT', 'READY'));

ALTER TABLE scene_rule_revisions
    ADD CONSTRAINT ck_scene_rule_revision_definition_status
    CHECK (definition_status IN ('DRAFT', 'READY'));
