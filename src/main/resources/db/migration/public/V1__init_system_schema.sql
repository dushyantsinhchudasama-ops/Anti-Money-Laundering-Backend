-- =============================================================================
-- System / Public Schema Migration: V1__init_system_schema.sql
-- Strictly derived from JPA Entities in com.tss.aml.entities.system
-- Target Schema: public
-- =============================================================================

-- 1. System Admin Table
CREATE TABLE public.system_admin (
    system_admin_id UUID PRIMARY KEY,
    system_admin_code VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    last_login TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 2. Tenants Table
CREATE TABLE public.tenants (
    tenant_id UUID PRIMARY KEY,
    tenant_code VARCHAR(255) NOT NULL UNIQUE,
    tenant_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'ONBOARDING',
    onboarded_by_admin_id UUID NOT NULL REFERENCES public.system_admin(system_admin_id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 3. Users Table
CREATE TABLE public.users (
    user_id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES public.tenants(tenant_id),
    user_code VARCHAR(20) NOT NULL,
    role VARCHAR(50) NOT NULL,
    employee_id VARCHAR(50),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    must_reset_password BOOLEAN NOT NULL DEFAULT TRUE,
    last_login TIMESTAMP,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 4. Rules Table
CREATE TABLE public.rules (
    rule_id UUID PRIMARY KEY,
    rule_code VARCHAR(255) NOT NULL UNIQUE,
    rule_name VARCHAR(255) NOT NULL,
    description TEXT,
    typology VARCHAR(50) NOT NULL,
    parameters TEXT,
    default_severity VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 5. Rule Version History Table
CREATE TABLE public.rule_version_history (
    history_id UUID PRIMARY KEY,
    rule_id UUID NOT NULL REFERENCES public.rules(rule_id),
    changed_by UUID NOT NULL REFERENCES public.system_admin(system_admin_id),
    previous_values TEXT,
    updated_values TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. Bank Rule Assignment Table
CREATE TABLE public.bank_rule_assignment (
    assignment_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES public.tenants(tenant_id),
    rule_id UUID NOT NULL REFERENCES public.rules(rule_id),
    assigned_by UUID NOT NULL REFERENCES public.system_admin(system_admin_id),
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. System Audit Log Table
CREATE TABLE public.system_audit_log (
    audit_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES public.system_admin(system_admin_id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255),
    details TEXT,
    ip_address VARCHAR(45),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
