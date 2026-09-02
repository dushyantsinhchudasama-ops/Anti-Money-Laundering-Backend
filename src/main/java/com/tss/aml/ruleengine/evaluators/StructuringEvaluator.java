package com.tss.aml.ruleengine.evaluators;

import com.tss.aml.dtos.rulesparam.StructuringConfigDto;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.ruleengine.AmlRuleEvaluator;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class StructuringEvaluator implements AmlRuleEvaluator {

    private final RuleParameterParser parser;
    private final FinancialTransactionRepository transactionRepository;

    @Override
    public boolean evaluate(FinancialTransaction financialTransaction, String params) {
        try {
            StructuringConfigDto config = parser.parse(params, StructuringConfigDto.class);
            BigDecimal threshold = config.getReportingThreshold();

            if (financialTransaction.getAmount() == null || financialTransaction.getAmount().compareTo(threshold) >= 0) {
                return false;
            }

            LocalDateTime endDate = financialTransaction.getTxnTimestamp();
            LocalDateTime startDate = endDate.minusDays(config.getWindowDays());

            BigDecimal sumAmount = transactionRepository.sumTransactionAmountsInWindow(
                    financialTransaction.getOriginatorAccount(), startDate, endDate);

            return sumAmount != null && sumAmount.compareTo(threshold) >= 0;

        } catch (Exception e) {
            log.error("Failed to execute Structuring Check", e);
            return false;
        }
    }

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.STRUCTURING_SMURFING;
    }
}

