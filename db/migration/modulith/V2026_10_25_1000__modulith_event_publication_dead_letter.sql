-- The resubmission job claims a publication (claimed_at) before it hands it to its listener again, so two tasks never
-- resubmit the same row, and parks one that keeps failing (dead_lettered_at) so it stops being retried and can be
-- found with a query. Spring Modulith ignores columns it does not know.
ALTER TABLE modulith.event_publication
    ADD COLUMN claimed_at       timestamptz,
    ADD COLUMN dead_lettered_at timestamptz;

CREATE INDEX event_publication_pending_idx ON modulith.event_publication (publication_date)
    WHERE completion_date IS NULL AND dead_lettered_at IS NULL;
CREATE INDEX event_publication_dead_lettered_idx ON modulith.event_publication (dead_lettered_at)
    WHERE dead_lettered_at IS NOT NULL;
