package com.revpay.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;  // Keep this as the primary key

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = true)
    private User sender;

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = true)
    private User receiver;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private String description;

    @CreationTimestamp
    private LocalDateTime timestamp;

    @Column(unique = true)  // This is the transaction reference (not a primary key)
    private String transactionRef;  // Renamed from 'id' to be clear

    public enum TransactionType {
        SEND, REQUEST, ADD_FUNDS, WITHDRAW, INVOICE_PAYMENT,LOAN_DISBURSEMENT,LOAN_REPAYMENT
    }

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, DECLINED, CANCELLED
    }
}