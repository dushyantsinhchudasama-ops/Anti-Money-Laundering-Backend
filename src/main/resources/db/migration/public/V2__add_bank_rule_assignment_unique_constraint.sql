ALTER TABLE public.bank_rule_assignment
    ADD CONSTRAINT uq_bank_rule_assignment UNIQUE (tenant_id, rule_id);
