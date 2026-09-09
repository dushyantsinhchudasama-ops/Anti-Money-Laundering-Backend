-- =============================================================================
-- Tenant Schema Migration: V2__add_sar_str_report_type.sql
-- Add report_type and pdf_content to sar_str table
-- =============================================================================

ALTER TABLE sar_str ADD COLUMN IF NOT EXISTS report_type VARCHAR(10);
ALTER TABLE sar_str ADD COLUMN IF NOT EXISTS pdf_content BYTEA;
