-- =============================================================================
-- Migration V3: Normalize email and tenant_code, and enforce UNIQUE constraint on users(email)
-- =============================================================================

-- 1. Data Normalization for existing records
UPDATE public.system_admin
SET email = LOWER(TRIM(email))
WHERE email IS NOT NULL;

UPDATE public.tenants
SET tenant_code = LOWER(TRIM(tenant_code))
WHERE tenant_code IS NOT NULL;

UPDATE public.users
SET email = LOWER(TRIM(email))
WHERE email IS NOT NULL;

-- 2. Enforce database-level UNIQUE constraint on public.users(email)
ALTER TABLE public.users
ADD CONSTRAINT users_email_key UNIQUE (email);
