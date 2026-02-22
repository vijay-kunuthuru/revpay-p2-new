package com.revpay.model.dto;

import com.revpay.model.entity.RiskTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanEligibilityDTO {

    private int creditScore;
    private RiskTier riskTier;
    private BigDecimal maxEligibleAmount;
    private boolean eligible;
}