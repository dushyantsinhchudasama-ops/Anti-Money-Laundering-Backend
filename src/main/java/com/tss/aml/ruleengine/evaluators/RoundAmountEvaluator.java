package com.tss.aml.ruleengine.evaluators;

import com.tss.aml.dtos.rulesparam.RoundAmountConfigDto;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.AmlRuleEvaluator;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@RequiredArgsConstructor
@Component
public class RoundAmountEvaluator implements AmlRuleEvaluator {

    private final RuleParameterParser parser;

    @Override
    public boolean evaluate(FinancialTransaction financialTransaction, String params) {
        try{
            RoundAmountConfigDto roundAmountConfig = parser.parse(params, RoundAmountConfigDto.class);
            BigDecimal threshold = roundAmountConfig.getModuloThreshold();

            if(threshold == null || threshold.compareTo(BigDecimal.ZERO) == 0) {
                return false;
            }

            return financialTransaction.getAmount().remainder(threshold).compareTo(BigDecimal.ZERO) == 0;

        } catch (Exception e) {
            log.error("Failed to parse Round Amount parameters", e);
            return false;
        }
    }

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.ROUND_AMOUNT_FLAGGING;
    }
}
