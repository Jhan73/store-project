-- Labels are captured when the event happens, so a row stays readable after its entity or actor is gone.
-- Rows written before this migration keep both null. Added on the partitioned parent, which reaches every partition.
ALTER TABLE audit.audit_log ADD COLUMN entity_label varchar(120);
ALTER TABLE audit.audit_log ADD COLUMN actor_label varchar(120);

-- Indexes on the partitioned parent propagate to every current and future partition.
-- The newest-first listing and its tiebreak, and the listing filtered by action.
CREATE INDEX audit_log_recent_idx ON audit.audit_log (occurred_at DESC, id DESC);
CREATE INDEX audit_log_action_idx ON audit.audit_log (action, occurred_at DESC, id DESC);
