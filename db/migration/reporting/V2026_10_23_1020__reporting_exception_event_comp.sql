ALTER TABLE reporting.exception_event DROP CONSTRAINT exception_event_type_check;

ALTER TABLE reporting.exception_event ADD CONSTRAINT exception_event_type_check
    CHECK (type IN ('LINE_VOID', 'WASTE', 'SALE_VOID', 'PAYMENT_VOID', 'CASH_OUT', 'SHIFT_DIFFERENCE', 'COMP'));
