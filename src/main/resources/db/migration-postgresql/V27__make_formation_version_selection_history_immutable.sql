CREATE OR REPLACE FUNCTION prevent_formation_version_selection_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'Immutable formation version selection history: % on % is not allowed',
        TG_OP, TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_formation_version_selection_history_immutable
BEFORE UPDATE OR DELETE ON formation_version_selection_history
FOR EACH ROW
EXECUTE FUNCTION prevent_formation_version_selection_history_mutation();
