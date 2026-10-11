-- Serves the per-line marker lookups: unacknowledged labels of a card's lines.
CREATE INDEX board_marker_line_idx ON preparation.board_marker (line_id) WHERE line_id IS NOT NULL;
