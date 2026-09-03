-- =============================================================================
-- Tenant Schema Migration Template: V1__init_tenant_schema.sql
-- Strictly derived from JPA Entities in com.tss.aml.entities.tenant
-- Target Schema: Dynamic per tenant (e.g. tenant_hdfc, tenant_icici, tenant_axis)
-- Executed programmatically per tenant via TenantMigrationService during onboarding
-- =============================================================================

-- 1. Account Table
CREATE TABLE account (
    account_id UUID PRIMARY KEY,
    account_number VARCHAR(30) NOT NULL UNIQUE,
    account_holder_name VARCHAR(200) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    bank_name VARCHAR(200),
    country_code VARCHAR(2) NOT NULL,
    risk_rating VARCHAR(50),
    opened_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 2. Transaction Batch Table
CREATE TABLE transaction_batch (
    batch_id UUID PRIMARY KEY,
    batch_code VARCHAR(255) NOT NULL UNIQUE,
    uploaded_by UUID NOT NULL REFERENCES public.users(user_id),
    file_reference VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    total_records INTEGER,
    alerts_generated_count INTEGER,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- 3. Financial Transaction Table
CREATE TABLE financial_transaction (
    transaction_id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES transaction_batch(batch_id),
    txn_no VARCHAR(255) NOT NULL,
    originator_account_id UUID NOT NULL REFERENCES account(account_id),
    amount NUMERIC(20, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    txn_type VARCHAR(50) NOT NULL,
    direction VARCHAR(50) NOT NULL,
    counterparty_name VARCHAR(200),
    counterparty_account_no VARCHAR(30),
    counterparty_bank VARCHAR(200),
    counterparty_country_code VARCHAR(2),
    txn_timestamp TIMESTAMP NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 4. Batch Validation Error Table
CREATE TABLE batch_validation_error (
    error_id BIGSERIAL PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES transaction_batch(batch_id),
    row_number INTEGER NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    error_message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. AML Case Table
CREATE TABLE aml_case (
    case_id UUID PRIMARY KEY,
    case_code VARCHAR(20) NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES public.users(user_id),
    assigned_to UUID REFERENCES public.users(user_id),
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    false_positive_rationale TEXT,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 6. Alerts Table
CREATE TABLE alerts (
    alert_id UUID PRIMARY KEY,
    alert_code VARCHAR(255) NOT NULL UNIQUE,
    transaction_id UUID NOT NULL REFERENCES financial_transaction(transaction_id),
    rule_id UUID NOT NULL REFERENCES public.rules(rule_id),
    severity VARCHAR(50) NOT NULL,
    alert_status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    case_id UUID REFERENCES aml_case(case_id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 7. Case Note Table
CREATE TABLE case_note (
    note_id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES aml_case(case_id),
    author_id UUID NOT NULL REFERENCES public.users(user_id),
    note_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8. Escalation Table
CREATE TABLE escalation (
    escalation_id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES aml_case(case_id),
    escalated_by UUID NOT NULL REFERENCES public.users(user_id),
    recipient_id UUID NOT NULL REFERENCES public.users(user_id),
    reason TEXT NOT NULL,
    escalated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. SAR / STR Table
CREATE TABLE sar_str (
    sar_str_id UUID PRIMARY KEY,
    case_id UUID NOT NULL UNIQUE REFERENCES aml_case(case_id),
    typology_category VARCHAR(100) NOT NULL,
    description_of_activity TEXT NOT NULL,
    basis_for_suspicion TEXT NOT NULL,
    supporting_evidence TEXT,
    reference_number VARCHAR(255) UNIQUE,
    pdf_reference VARCHAR(255),
    filed_by UUID REFERENCES public.users(user_id),
    submitted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 10. Audit Log Table
CREATE TABLE audit_log (
    audit_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES public.users(user_id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255),
    details TEXT,
    ip_address VARCHAR(45),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 11. Notification Table
CREATE TABLE notification (
    notification_id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES public.users(user_id),
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
