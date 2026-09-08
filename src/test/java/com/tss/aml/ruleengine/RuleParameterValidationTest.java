package com.tss.aml.ruleengine;

import com.tss.aml.enums.RuleTypology;
import com.tss.aml.ruleengine.validators.RuleParameterValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RuleParameterValidationTest {

    @Autowired
    private RuleParameterValidationService validationService;

    @Test
    @DisplayName("Valid 1: DORMANT_ACCOUNT configuration")
    void validDormantAccount() {
        Map<String, Object> params = Map.of(
                "dormantDays", 180,
                "minAmountThreshold", new BigDecimal("100000.00")
        );

        Object result = validationService.validate(RuleTypology.DORMANT_ACCOUNT, params);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Valid 2: GEOGRAPHIC_RISK configuration")
    void validGeographicRisk() {
        Map<String, Object> params = Map.of(
                "highRiskCountries", List.of("IR", "KP", "SY")
        );

        Object result = validationService.validate(RuleTypology.GEOGRAPHIC_RISK, params);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Valid 3: PEP_EXPOSURE configuration")
    void validPepExposure() {
        Map<String, Object> params = Map.of(
                "minAmountThreshold", new BigDecimal("50000.00")
        );

        Object result = validationService.validate(RuleTypology.PEP_EXPOSURE, params);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Valid 4: RAPID_PASS_THROUGH configuration")
    void validRapidPassThrough() {
        Map<String, Object> params = Map.of(
                "windowHours", 24,
                "minAmountThreshold", new BigDecimal("200000.00"),
                "passThroughRatio", 0.85
        );

        Object result = validationService.validate(RuleTypology.RAPID_PASS_THROUGH, params);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Valid 5: ROUND_AMOUNT_FLAGGING configuration")
    void validRoundAmountFlagging() {
        Map<String, Object> params = Map.of(
                "moduloThreshold", new BigDecimal("10000.00")
        );

        Object result = validationService.validate(RuleTypology.ROUND_AMOUNT_FLAGGING, params);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Valid 6: STRUCTURING_SMURFING configuration")
    void validStructuringSmurfing() {
        Map<String, Object> params = Map.of(
                "windowDays", 7,
                "reportingThreshold", new BigDecimal("500000.00")
        );

        Object result = validationService.validate(RuleTypology.STRUCTURING_SMURFING, params);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Valid 7: VELOCITY_CHECK configuration")
    void validVelocityCheck() {
        Map<String, Object> params = Map.of(
                "windowDays", 30,
                "maxTransactionCount", 15
        );

        Object result = validationService.validate(RuleTypology.VELOCITY_CHECK, params);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Invalid 8: Reject extra / unknown parameter")
    void rejectExtraParameter() {
        Map<String, Object> params = Map.of(
                "dormantDays", 180,
                "minAmountThreshold", new BigDecimal("100000.00"),
                "unknownField", 123
        );

        assertThatThrownBy(() -> validationService.validate(RuleTypology.DORMANT_ACCOUNT, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown configuration parameter: parameters.unknownField");
    }

    @Test
    @DisplayName("Invalid 9: Reject missing required parameter")
    void rejectMissingParameter() {
        Map<String, Object> params = Map.of(
                "dormantDays", 180
        );

        assertThatThrownBy(() -> validationService.validate(RuleTypology.DORMANT_ACCOUNT, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required configuration parameter is missing: parameters.minAmountThreshold");
    }

    @Test
    @DisplayName("Invalid 10: Reject wrong parameter key name")
    void rejectWrongKeyName() {
        Map<String, Object> params = Map.of(
                "dormantDay", 180,
                "minAmountThreshold", new BigDecimal("100000.00")
        );

        assertThatThrownBy(() -> validationService.validate(RuleTypology.DORMANT_ACCOUNT, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown configuration parameter: parameters.dormantDay");
    }

    @Test
    @DisplayName("Invalid 11: Reject wrong datatype (String for Integer/BigDecimal)")
    void rejectWrongDatatype() {
        Map<String, Object> params = Map.of(
                "dormantDays", "180",
                "minAmountThreshold", new BigDecimal("100000.00")
        );

        assertThatThrownBy(() -> validationService.validate(RuleTypology.DORMANT_ACCOUNT, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid datatype for parameters.dormantDays");
    }

    @Test
    @DisplayName("Invalid 12: Reject null required parameter value")
    void rejectNullValue() {
        Map<String, Object> params = new HashMap<>();
        params.put("dormantDays", 180);
        params.put("minAmountThreshold", null);

        assertThatThrownBy(() -> validationService.validate(RuleTypology.DORMANT_ACCOUNT, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required configuration parameter cannot be null: parameters.minAmountThreshold");
    }

    @Test
    @DisplayName("Invalid 13: Reject invalid value according to Bean Validation (e.g. negative days / ratio > 1.0)")
    void rejectBeanValidationViolation() {
        Map<String, Object> params = Map.of(
                "dormantDays", -10,
                "minAmountThreshold", new BigDecimal("100000.00")
        );

        assertThatThrownBy(() -> validationService.validate(RuleTypology.DORMANT_ACCOUNT, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dormantDays");

        Map<String, Object> invalidRatioParams = Map.of(
                "windowHours", 24,
                "minAmountThreshold", new BigDecimal("200000.00"),
                "passThroughRatio", 1.5
        );

        assertThatThrownBy(() -> validationService.validate(RuleTypology.RAPID_PASS_THROUGH, invalidRatioParams))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passThroughRatio");
    }

    @Test
    @DisplayName("Invalid 14: Reject unsupported / unregistered RuleTypology")
    void rejectUnregisteredTypology() {
        Map<String, Object> params = Map.of("testKey", "testVal");

        assertThatThrownBy(() -> validationService.validate(RuleTypology.LAYERING, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No configuration validator registered for typology: LAYERING");
    }
}
