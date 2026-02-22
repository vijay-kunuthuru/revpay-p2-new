package com.revpay.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionFilterDTO {
    private String type;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
}