package com.revpay.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanApplyDTO {


    @NotNull(message = "Loan amount is required")
    @DecimalMin(value = "1000.00", message = "Minimum loan amount is ₹1,000")
    @DecimalMax(value = "1000000.00", message = "Maximum loan amount is ₹10,00,000")
    private BigDecimal amount;

    @NotNull(message = "Tenure is required")
    @Min(value = 3, message = "Minimum tenure is 3 months")
    @Max(value = 60, message = "Maximum tenure is 60 months")
    private Integer tenureMonths;

    @NotBlank(message = "Loan purpose is required")
    @Size(max = 255, message = "Purpose must not exceed 255 characters")
    private String purpose;
}