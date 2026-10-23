ALTER TABLE scene_rule_groups
    ADD COLUMN composition_mode VARCHAR(20) NOT NULL DEFAULT 'COUNT';

ALTER TABLE scene_rule_groups
    ADD CONSTRAINT ck_scene_rule_group_composition_mode
    CHECK (composition_mode IN ('COUNT', 'IDENTIFIED'));

ALTER TABLE scene_rule_revision_groups
    ADD COLUMN composition_mode VARCHAR(20) NOT NULL DEFAULT 'COUNT';

ALTER TABLE scene_rule_revision_groups
    ADD CONSTRAINT ck_scene_rule_revision_group_composition_mode
    CHECK (composition_mode IN ('COUNT', 'IDENTIFIED'));

CREATE TABLE scene_rule_group_members (
    scene_rule_group_id BIGINT NOT NULL
        REFERENCES scene_rule_groups(id) ON DELETE CASCADE,
    singer_id BIGINT NOT NULL
        REFERENCES singers(id) ON DELETE RESTRICT,
    member_order INTEGER NOT NULL,

    CONSTRAINT pk_scene_rule_group_members
        PRIMARY KEY (scene_rule_group_id, singer_id),
    CONSTRAINT uq_scene_rule_group_member_order
        UNIQUE (scene_rule_group_id, member_order),
    CONSTRAINT ck_scene_rule_group_member_order
        CHECK (member_order >= 0)
);

CREATE TABLE scene_rule_revision_group_members (
    revision_id BIGINT NOT NULL,
    group_order INTEGER NOT NULL,
    singer_id BIGINT NOT NULL,
    member_order INTEGER NOT NULL,

    CONSTRAINT pk_scene_rule_revision_group_members
        PRIMARY KEY (revision_id, group_order, member_order),
    CONSTRAINT uq_scene_rule_revision_group_member
        UNIQUE (revision_id, group_order, singer_id),
    CONSTRAINT fk_scene_rule_revision_group_member_group
        FOREIGN KEY (revision_id, group_order)
        REFERENCES scene_rule_revision_groups(revision_id, group_order)
        ON DELETE CASCADE,
    CONSTRAINT ck_scene_rule_revision_group_member_order
        CHECK (member_order >= 0)
);
