package com.revpay.repository;

import com.revpay.model.entity.Transaction;
import com.revpay.model.entity.Transaction.TransactionStatus;
import com.revpay.model.entity.Transaction.TransactionType;
import com.revpay.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {


    List<Transaction> findBySenderOrReceiverOrderByTimestampDesc(User sender, User receiver);


    List<Transaction> findByReceiverUserIdAndTypeAndStatus(
            Long receiverUserId,
            TransactionType type,
            TransactionStatus status
    );


    List<Transaction> findBySenderUserIdAndTypeAndStatus(
            Long senderUserId,
            TransactionType type,
            TransactionStatus status
    );



    List<Transaction> findByReceiverUserIdAndStatusAndTypeIn(
            Long businessId,
            TransactionStatus status,
            List<TransactionType> types
    );


    List<Transaction> findByReceiverUserIdAndStatusAndTimestampBetween(
            Long businessId,
            TransactionStatus status,
            LocalDateTime start,
            LocalDateTime end
    );
}