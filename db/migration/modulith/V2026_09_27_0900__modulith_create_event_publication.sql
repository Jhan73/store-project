CREATE SCHEMA modulith;
GRANT USAGE ON SCHEMA modulith TO app;

-- Spring Modulith's own event publication registry (schema-jdbc.schema v2, spring-modulith-events-jdbc 2.1.1);
-- schema-initialization stays disabled so migrator, not the library, owns this DDL.
CREATE TABLE modulith.event_publication (
    id                     uuid        NOT NULL PRIMARY KEY,
    listener_id            text        NOT NULL,
    event_type             text        NOT NULL,
    serialized_event       text        NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    status                 text,
    completion_attempts    integer,
    last_resubmission_date timestamptz
);

CREATE INDEX event_publication_serialized_event_hash_idx ON modulith.event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON modulith.event_publication (completion_date);
