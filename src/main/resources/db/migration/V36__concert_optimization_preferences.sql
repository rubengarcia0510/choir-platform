CREATE TABLE concert_optimization_preferences (
    concert_id BIGINT PRIMARY KEY
        REFERENCES concerts(id) ON DELETE CASCADE,
    distance INTEGER NOT NULL,
    crossings INTEGER NOT NULL,
    compactness INTEGER NOT NULL,
    section_continuity INTEGER NOT NULL,
    CONSTRAINT ck_concert_preferences_distance
        CHECK (distance BETWEEN 0 AND 100),
    CONSTRAINT ck_concert_preferences_crossings
        CHECK (crossings BETWEEN 0 AND 100),
    CONSTRAINT ck_concert_preferences_compactness
        CHECK (compactness BETWEEN 0 AND 100),
    CONSTRAINT ck_concert_preferences_section_continuity
        CHECK (section_continuity BETWEEN 0 AND 100),
    CONSTRAINT ck_concert_preferences_total
        CHECK (
            distance + crossings + compactness + section_continuity = 100
        )
);
