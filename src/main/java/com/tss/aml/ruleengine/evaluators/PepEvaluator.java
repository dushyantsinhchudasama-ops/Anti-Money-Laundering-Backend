package com.tss.aml.ruleengine.evaluators;

import com.tss.aml.dtos.rulesparam.PepConfigDto;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.RiskRating;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.AmlRuleEvaluator;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PepEvaluator implements AmlRuleEvaluator {

    private final RuleParameterParser parser;

    @Override
    public boolean evaluate(FinancialTransaction financialTransaction, String params) {
        try {
            PepConfigDto config = parser.parse(params, PepConfigDto.class);
            BigDecimal threshold = config.getMinAmountThreshold();

            if (financialTransaction.getAmount() == null || threshold == null) {
                return false;
            }

            boolean isHighRisk = financialTransaction.getOriginatorAccount() != null
                    && RiskRating.HIGH.equals(financialTransaction.getOriginatorAccount().getRiskRating());

            boolean isAmountExceeded = financialTransaction.getAmount().compareTo(threshold) >= 0;

            return isHighRisk && isAmountExceeded;
        } catch (Exception e) {
            log.error("Failed to execute PEP Exposure check", e);
            return false;
        }
    }

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.PEP_EXPOSURE;
    }
}
