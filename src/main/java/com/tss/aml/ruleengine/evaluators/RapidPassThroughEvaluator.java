package com.tss.aml.ruleengine.evaluators;

import com.tss.aml.dtos.rulesparam.RapidPassThroughConfigDto;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.enums.TransactionDirection;
import com.tss.aml.repositories.FinancialTransactionRepository;
import com.tss.aml.ruleengine.AmlRuleEvaluator;
import com.tss.aml.ruleengine.util.RuleParameterParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RapidPassThroughEvaluator implements AmlRuleEvaluator {

    private final RuleParameterParser parser;
    private final FinancialTransactionRepository transactionRepository;

    @Override
    public boolean evaluate(FinancialTransaction financialTransaction, String params) {
        try {
            RapidPassThroughConfigDto config = parser.parse(params, RapidPassThroughConfigDto.class);
            LocalDateTime endDate = financialTransaction.getTxnTimestamp();
            LocalDateTime startDate = endDate.minusHours(config.getWindowHours());

            BigDecimal incoming = transactionRepository.sumAmountByDirectionInWindow(
                    financialTransaction.getOriginatorAccount(), TransactionDirection.IN, startDate, endDate);

            BigDecimal outgoing = transactionRepository.sumAmountByDirectionInWindow(
                    financialTransaction.getOriginatorAccount(), TransactionDirection.OUT, startDate, endDate);

            if (incoming == null || outgoing == null || incoming.compareTo(config.getMinAmountThreshold()) < 0) {
                return false;
            }

            BigDecimal ratio = outgoing.divide(incoming, 2, RoundingMode.HALF_UP);
            return ratio.doubleValue() >= config.getPassThroughRatio();

        } catch (Exception e) {
            log.error("Failed to execute Rapid Pass-Through check", e);
            return false;
        }
    }

    @Override
    public RuleTypology getSupportedTypology() {
        return RuleTypology.RAPID_PASS_THROUGH;
    }
}
