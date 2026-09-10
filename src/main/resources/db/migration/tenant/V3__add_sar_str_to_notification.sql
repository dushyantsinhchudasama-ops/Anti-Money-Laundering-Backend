-- =============================================================================
-- Tenant Schema Migration: V3__add_sar_str_to_notification.sql
-- Add sar_str_id column to notification table with FK and database unique constraint
-- =============================================================================

ALTER TABLE notification
ADD COLUMN IF NOT EXISTS sar_str_id UUID REFERENCES sar_str(sar_str_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_notification_recipient_sar_str'
    ) THEN
        ALTER TABLE notification ADD CONSTRAINT uq_notification_recipient_sar_str UNIQUE (recipient_id, sar_str_id);
    END IF;
END $$;
