-- One row per shift and recipient. CLAIMED is a lease: a delivery that crashed before sending leaves a claim that
-- another delivery may take over once it is older than the lease. SENT is final and carries the real send time.
CREATE TABLE reporting.shift_summary_delivery (
    shift_id   uuid         NOT NULL,
    recipient  varchar(254) NOT NULL,
    status     varchar(10)  NOT NULL,
    claimed_at timestamptz  NOT NULL,
    sent_at    timestamptz,
    PRIMARY KEY (shift_id, recipient),
    CONSTRAINT shift_summary_delivery_status_check CHECK (status IN ('CLAIMED', 'SENT')),
    CONSTRAINT shift_summary_delivery_sent_check CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

-- Replaces the per-shift claim of the previous migration, which was never released to an environment.
DROP TABLE reporting.shift_summary_email;
