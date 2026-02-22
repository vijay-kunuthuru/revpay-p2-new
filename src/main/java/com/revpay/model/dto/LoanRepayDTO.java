package com.revpay.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoanRepayDTO {

    @NotNull(message = "Loan ID is required")
    private Long loanId;

}