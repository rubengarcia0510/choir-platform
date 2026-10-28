CREATE OR REPLACE FUNCTION prevent_transition_publication_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Immutable transition publication history: % on % is not allowed',
        TG_OP, TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_transition_rule_evaluations_immutable
BEFORE UPDATE OR DELETE ON transition_rule_evaluations
FOR EACH ROW
EXECUTE FUNCTION prevent_transition_publication_history_mutation();

CREATE TRIGGER trg_transition_rule_evaluation_results_immutable
BEFORE UPDATE OR DELETE ON transition_rule_evaluation_results
FOR EACH ROW
EXECUTE FUNCTION prevent_transition_publication_history_mutation();

CREATE TRIGGER trg_transition_rule_evaluation_warnings_immutable
BEFORE UPDATE OR DELETE ON transition_rule_evaluation_warnings
FOR EACH ROW
EXECUTE FUNCTION prevent_transition_publication_history_mutation();

CREATE TRIGGER trg_transition_publication_evidence_immutable
BEFORE UPDATE OR DELETE ON transition_publication_evidence
FOR EACH ROW
EXECUTE FUNCTION prevent_transition_publication_history_mutation();

CREATE TRIGGER trg_transition_publication_decisions_immutable
BEFORE UPDATE OR DELETE ON transition_publication_decisions
FOR EACH ROW
EXECUTE FUNCTION prevent_transition_publication_history_mutation();
