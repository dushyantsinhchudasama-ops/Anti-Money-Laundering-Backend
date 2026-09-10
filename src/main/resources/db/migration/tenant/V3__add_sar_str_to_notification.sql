-- =============================================================================
-- Tenant Schema Migration: V3__add_sar_str_to_notification.sql
-- Add sar_str_id column to notification table with FK and database unique constraint
-- =============================================================================

ALTER TABLE notification
ADD COLUMN sar_str_id UUID REFERENCES sar_str(sar_str_id);

ALTER TABLE notification
ADD CONSTRAINT uq_notification_recipient_sar_str UNIQUE (recipient_id, sar_str_id);
