package com.tss.aml.enums;

/**
 * Recognised money-laundering typologies a Rule can codify (SRS 1.3 / 4.2).
 */
public enum RuleTypology {
    STRUCTURING_SMURFING,
    LAYERING,
    PEP_EXPOSURE,
    VELOCITY_CHECK,
    ROUND_AMOUNT_FLAGGING,
    GEOGRAPHIC_RISK,
    FRAUD_RELATED_ML
}
