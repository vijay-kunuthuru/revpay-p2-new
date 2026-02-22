package com.revpay.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TransferCompletedEvent {
    private final Long senderId;
    private final Long receiverId;
    private final BigDecimal amount;
    private final String senderName;
    private final String receiverName;
}