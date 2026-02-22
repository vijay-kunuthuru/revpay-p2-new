package com.revpay.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoanApprovalDTO {

    @NotNull(message = "Loan ID is required")
    private Long loanId;

    private boolean approved;

    @DecimalMin(value = "1.0", message = "Interest rate must be at least 1%")
    @DecimalMax(value = "36.0", message = "Interest rate cannot exceed 36%")
    private BigDecimal interestRate;
}