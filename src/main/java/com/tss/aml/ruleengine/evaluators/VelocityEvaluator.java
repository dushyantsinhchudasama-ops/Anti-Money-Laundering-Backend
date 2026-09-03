package com.tss.aml.ruleengine.evaluators;

import com.tss.aml.dtos.rulesparam.VelocityConfigDto;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.ruleengine.AmlRuleEvaluator;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class VelocityEvaluator implements AmlRuleEvaluator {

    private final RuleParameterParser parser;
    private final FinancialTransactionRepository transactionRepository;

    @Override
    public boolean evaluate(FinancialTransaction financialTransaction, String params) {
        try {
            VelocityConfigDto config = parser.parse(params, VelocityConfigDto.class);
            LocalDateTime endDate = financialTransaction.getTxnTimestamp();
            LocalDateTime startDate = endDate.minusDays(config.getWindowDays());

            Integer count = transactionRepository.countTransactionsInWindow(
                    financialTransaction.getOriginatorAccount(), startDate, endDate);

            return count != null && count > config.getMaxTransactionCount();
        } catch (Exception e) {
            log.error("Failed to execute Velocity Check", e);
            return false;
        }
    }

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.VELOCITY_CHECK;
    }
}

