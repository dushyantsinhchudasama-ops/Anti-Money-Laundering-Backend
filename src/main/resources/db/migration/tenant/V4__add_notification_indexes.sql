-- =============================================================================
-- Tenant Schema Migration: V4__add_notification_indexes.sql
-- Add composite indexes to tenant notification table for instant query performance
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_notification_recipient_created
ON notification (recipient_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_recipient_unread
ON notification (recipient_id, is_read, created_at DESC);
