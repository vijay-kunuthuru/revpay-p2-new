package com.revpay.model.dto;

import com.revpay.model.entity.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanResponseDTO {

    private Long loanId;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private BigDecimal emiAmount;
    private BigDecimal remainingAmount;
    private LoanStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
}