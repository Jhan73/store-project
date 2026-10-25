-- Spring Modulith picks the oldest FAILED rows of event_publication before any filter of ours runs, so a parked
-- publication must leave that table. It keeps its id and payload here, where it can be queried and redelivered by hand.
-- The dead_lettered_at column of the previous migration was never released: parking moved to this table.
CREATE TABLE modulith.event_publication_dead_letter (
    id                  uuid        NOT NULL PRIMARY KEY,
    listener_id         text        NOT NULL,
    event_type          text        NOT NULL,
    serialized_event    text        NOT NULL,
    publication_date    timestamptz NOT NULL,
    completion_attempts integer     NOT NULL,
    dead_lettered_at    timestamptz NOT NULL
);

CREATE INDEX event_publication_dead_letter_at_idx ON modulith.event_publication_dead_letter (dead_lettered_at);

DROP INDEX modulith.event_publication_dead_lettered_idx;
DROP INDEX modulith.event_publication_pending_idx;
ALTER TABLE modulith.event_publication DROP COLUMN dead_lettered_at;

CREATE INDEX event_publication_pending_idx ON modulith.event_publication (publication_date)
    WHERE completion_date IS NULL;
