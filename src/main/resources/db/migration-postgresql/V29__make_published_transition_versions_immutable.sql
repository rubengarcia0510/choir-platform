CREATE OR REPLACE FUNCTION prevent_published_transition_version_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'PUBLISHED' THEN
        RAISE EXCEPTION
            'Published transition versions are immutable: % is not allowed',
            TG_OP;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_published_transition_versions_immutable
BEFORE UPDATE OR DELETE ON transition_versions
FOR EACH ROW
EXECUTE FUNCTION prevent_published_transition_version_mutation();
