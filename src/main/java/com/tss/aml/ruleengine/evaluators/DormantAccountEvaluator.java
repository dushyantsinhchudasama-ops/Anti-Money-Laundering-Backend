package com.tss.aml.ruleengine.evaluators;

import com.tss.aml.dtos.rulesparam.DormantAccountConfigDto;
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
public class DormantAccountEvaluator implements AmlRuleEvaluator {

    private final RuleParameterParser parser;
    private final FinancialTransactionRepository transactionRepository;

    @Override
    public boolean evaluate(FinancialTransaction financialTransaction, String params) {
        try {
            DormantAccountConfigDto config = parser.parse(params, DormantAccountConfigDto.class);
            BigDecimal threshold = config.getMinAmountThreshold();

            if (financialTransaction.getAmount() == null || financialTransaction.getAmount().compareTo(threshold) < 0) {
                return false;
            }

            LocalDateTime beforeDate = financialTransaction.getTxnTimestamp();
            LocalDateTime startDate = beforeDate.minusDays(config.getDormantDays());

            Integer priorTxnCount = transactionRepository.countPriorTransactions(financialTransaction.getOriginatorAccount(), startDate, beforeDate);

            return priorTxnCount == null || priorTxnCount == 0;

        } catch (Exception e) {
            log.error("Failed to execute Dormant Account Reactivation check", e);
            return false;
        }
    }

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.DORMANT_ACCOUNT_REACTIVATION;
    }
}
