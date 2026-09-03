package com.tss.aml.ruleengine.evaluators;

import com.tss.aml.dtos.rulesparam.GeographicRiskConfigDto;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.AmlRuleEvaluator;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeographicRiskRuleEvaluator implements AmlRuleEvaluator {

    private final RuleParameterParser parser;

    @Override
    public boolean evaluate(FinancialTransaction financialTransaction, String params) {
        try {
            GeographicRiskConfigDto config = parser.parse(params, GeographicRiskConfigDto.class);
            if (config == null || config.getHighRiskCountries() == null) {
                return false;
            }

            boolean originatorRisk = financialTransaction.getCountryCode() != null
                    && config.getHighRiskCountries().contains(financialTransaction.getCountryCode());
            boolean counterpartyRisk = financialTransaction.getCounterpartyCountryCode() != null
                    && config.getHighRiskCountries().contains(financialTransaction.getCounterpartyCountryCode());

            return originatorRisk || counterpartyRisk;
        } catch (Exception e) {
            log.error("Failed to parse Geographical Risk parameters", e);
            return false;
        }
    }

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.GEOGRAPHIC_RISK;
    }
}

