package com.tss.aml.enums;

/**
 * Standard FATF/FIU typology categories for regulatory SAR/STR reporting (SRS 3.3.6 / 4.2).
 */
public enum FiuTypologyCategory {
    STRUCTURING,
    LAYERING,
    PEP_TRANSACTION,
    FRAUD_RELATED_ML,
    VELOCITY_CHECK,
    GEOGRAPHIC_RISK,
    RAPID_PASS_THROUGH,
    DORMANT_ACCOUNT,
    UTURN_TRANSACTION,
    CIRCULAR_LOOPING,
    OTHER_SUSPICIOUS_ACTIVITY
}
