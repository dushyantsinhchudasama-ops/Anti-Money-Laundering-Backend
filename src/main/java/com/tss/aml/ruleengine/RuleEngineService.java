package com.tss.aml.ruleengine;

import com.tss.aml.entities.system.Rule;
import com.tss.aml.entities.tenant.Alert;
import com.tss.aml.entities.tenant.FinancialTransaction;
import com.tss.aml.enums.AlertStatus;
import com.tss.aml.enums.RuleTypology;
import com.tss.aml.repositories.AlertRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuleEngineService {
    private final List<AmlRuleEvaluator> evaluatorList;
    private final AlertRepository alertRepository;

    private Map<RuleTypology, AmlRuleEvaluator> evaluatorMap;

    @PostConstruct
    public void init() {
        evaluatorMap = evaluatorList.stream()
                .collect(Collectors.toMap(AmlRuleEvaluator::getSupportedTypology, e -> e));
    }

    public List<Alert> evaluateBatch(List<FinancialTransaction> batchTransactions, List<Rule> activeRules) {
        if (batchTransactions == null || batchTransactions.isEmpty() || activeRules == null || activeRules.isEmpty()) {
            return Collections.emptyList();
        }

        List<Alert> generatedAlerts = batchTransactions.parallelStream()
                .flatMap(txn -> activeRules.stream()
                        .map(rule -> evaluateRule(txn, rule))
                        .filter(Objects::nonNull)
                )
                .toList();

        if (!generatedAlerts.isEmpty()) {
            int batchSize = 500;
            for (int i = 0; i < generatedAlerts.size(); i += batchSize) {
                int end = Math.min(generatedAlerts.size(), i + batchSize);
                alertRepository.saveAll(generatedAlerts.subList(i, end));
            }
        }

        return generatedAlerts;
    }

    private Alert evaluateRule(FinancialTransaction txn, Rule rule) {
        AmlRuleEvaluator evaluator = evaluatorMap.get(rule.getTypology());
        if (evaluator != null && evaluator.evaluate(txn, rule.getParameters())) {
            return Alert.builder()
                    .alertCode("ALT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .transaction(txn)
                    .rule(rule)
                    .severity(rule.getDefaultSeverity())
                    .alertStatus(AlertStatus.OPEN)
                    .build();
        }
        return null;
    }
}


