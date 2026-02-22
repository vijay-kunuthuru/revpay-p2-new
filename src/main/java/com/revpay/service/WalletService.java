package com.revpay.service;

import com.revpay.model.entity.*;
import com.revpay.repository.*;
import com.revpay.model.dto.TransactionRequest;
import com.revpay.model.dto.WalletAnalyticsDTO;
import com.revpay.model.dto.TransferCompletedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WalletService {

    @Autowired private WalletRepository walletRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;

    private String generateRef() {
        return "TXN-" + System.currentTimeMillis();
    }

    private void checkDailyLimit(User sender, BigDecimal newAmount) {
        BigDecimal dailyLimit = new BigDecimal("50000.00");
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.now().with(LocalTime.MAX);

        BigDecimal totalSentToday = transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(sender, sender)
                .stream()
                .filter(t -> t.getSender() != null && t.getSender().getUserId().equals(sender.getUserId()))
                .filter(t -> t.getType() == Transaction.TransactionType.SEND)
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .filter(t -> t.getTimestamp().isAfter(startOfDay) && t.getTimestamp().isBefore(endOfDay))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalSentToday.add(newAmount).compareTo(dailyLimit) > 0) {
            throw new RuntimeException("Daily transfer limit of INR50,000 exceeded!");
        }
    }

    public List<Transaction> getTransactionHistory(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user);
    }

    public List<Transaction> getMyHistory(User user, String type) {
        List<Transaction> all = transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user);
        if (type != null && !type.isEmpty()) {
            return all.stream()
                    .filter(t -> t.getType().name().equalsIgnoreCase(type))
                    .collect(Collectors.toList());
        }
        return all;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Transaction requestMoney(Long requesterId, String targetEmail, BigDecimal amount) {
        User requester = userRepository.findById(requesterId).orElseThrow(() -> new RuntimeException("Requester not found"));
        User target = userRepository.findByEmail(targetEmail).orElseThrow(() -> new RuntimeException("Target user not found"));

        Transaction request = new Transaction();
        request.setSender(target);
        request.setReceiver(requester);
        request.setAmount(amount);
        request.setType(Transaction.TransactionType.REQUEST);
        request.setStatus(Transaction.TransactionStatus.PENDING);
        request.setTransactionRef(generateRef());

        return transactionRepository.save(request);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Transaction acceptRequest(Long transactionId, String pin) {
        Transaction request = transactionRepository.findById(transactionId).orElseThrow(() -> new RuntimeException("Request not found"));
        TransactionRequest paymentReq = new TransactionRequest(request.getReceiver().getEmail(), request.getAmount(), "Payment for Request", pin);
        sendMoney(request.getSender().getUserId(), paymentReq);
        request.setStatus(Transaction.TransactionStatus.COMPLETED);
        return transactionRepository.save(request);
    }

    public BigDecimal getBalance(Long userId) {
        return walletRepository.findByUserUserId(userId)
                .map(Wallet::getBalance)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user ID: " + userId));
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Transaction sendMoney(Long senderId, TransactionRequest request) {
        User sender = userRepository.findById(senderId).orElseThrow(() -> new RuntimeException("Sender not found"));
        User receiver = userRepository.findByEmail(request.getReceiverIdentifier()).orElseThrow(() -> new RuntimeException("Receiver not found"));

        if (!passwordEncoder.matches(request.getTransactionPin(), sender.getTransactionPinHash())) {
            throw new RuntimeException("Invalid Transaction PIN!");
        }

        checkDailyLimit(sender, request.getAmount());

        Long firstLockId = Math.min(senderId, receiver.getUserId());
        Long secondLockId = Math.max(senderId, receiver.getUserId());

        Wallet firstWallet = walletRepository.findByUserUserIdForUpdate(firstLockId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
        Wallet secondWallet = walletRepository.findByUserUserIdForUpdate(secondLockId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        Wallet senderWallet = senderId.equals(firstLockId) ? firstWallet : secondWallet;
        Wallet receiverWallet = receiver.getUserId().equals(firstLockId) ? firstWallet : secondWallet;

        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient balance!");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(request.getAmount()));
        receiverWallet.setBalance(receiverWallet.getBalance().add(request.getAmount()));

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        Transaction tx = new Transaction();
        tx.setSender(sender);
        tx.setReceiver(receiver);
        tx.setAmount(request.getAmount());
        tx.setType(Transaction.TransactionType.SEND);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setTransactionRef(generateRef());

        Transaction savedTx = transactionRepository.save(tx);

        eventPublisher.publishEvent(new TransferCompletedEvent(
                sender.getUserId(),
                receiver.getUserId(),
                request.getAmount(),
                sender.getFullName(),
                receiver.getFullName()
        ));

        return savedTx;
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Transaction addFunds(Long userId, BigDecimal amount, String description) {
        Wallet wallet = walletRepository.findByUserUserIdForUpdate(userId).orElseThrow(() -> new RuntimeException("Wallet not found"));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setReceiver(wallet.getUser());
        tx.setAmount(amount);
        tx.setType(Transaction.TransactionType.ADD_FUNDS);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setTransactionRef(generateRef());

        return transactionRepository.save(tx);
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Transaction withdrawFunds(Long userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserUserIdForUpdate(userId).orElseThrow(() -> new RuntimeException("Wallet not found"));
        if (wallet.getBalance().compareTo(amount) < 0) throw new RuntimeException("Insufficient balance");

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setSender(wallet.getUser());
        tx.setAmount(amount);
        tx.setType(Transaction.TransactionType.WITHDRAW);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setTransactionRef(generateRef());

        return transactionRepository.save(tx);
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Transaction payInvoice(Long userId, Long invoiceId, String pin) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(pin, user.getTransactionPinHash())) throw new RuntimeException("Invalid PIN!");

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new RuntimeException("Invoice not found"));

        Long businessUserId = invoice.getBusinessProfile().getUser().getUserId();

        Long firstLockId = Math.min(userId, businessUserId);
        Long secondLockId = Math.max(userId, businessUserId);

        Wallet firstWallet = walletRepository.findByUserUserIdForUpdate(firstLockId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
        Wallet secondWallet = walletRepository.findByUserUserIdForUpdate(secondLockId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        Wallet senderWallet = userId.equals(firstLockId) ? firstWallet : secondWallet;
        Wallet receiverWallet = businessUserId.equals(firstLockId) ? firstWallet : secondWallet;

        if (senderWallet.getBalance().compareTo(invoice.getTotalAmount()) < 0) throw new RuntimeException("Insufficient balance!");

        senderWallet.setBalance(senderWallet.getBalance().subtract(invoice.getTotalAmount()));
        receiverWallet.setBalance(receiverWallet.getBalance().add(invoice.getTotalAmount()));

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        invoice.setStatus(Invoice.InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        Transaction tx = new Transaction();
        tx.setSender(user);
        tx.setReceiver(invoice.getBusinessProfile().getUser());
        tx.setAmount(invoice.getTotalAmount());
        tx.setType(Transaction.TransactionType.INVOICE_PAYMENT);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setTransactionRef(generateRef());

        return transactionRepository.save(tx);
    }

    public PaymentMethod addCard(Long userId, PaymentMethod card) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        boolean exists = paymentMethodRepository.findByUser(user).stream()
                .anyMatch(c -> c.getCardNumber().equals(card.getCardNumber()));
        if (exists) throw new RuntimeException("This card is already linked to your account.");

        card.setUser(user);
        return paymentMethodRepository.save(card);
    }

    @Transactional
    public void deleteCard(Long userId, Long cardId) {
        PaymentMethod card = paymentMethodRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found"));

        if (!card.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("You are not authorized to delete this card.");
        }

        paymentMethodRepository.delete(card);
    }

    public List<PaymentMethod> getCards(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return paymentMethodRepository.findByUser(user);
    }

    @Transactional
    public void setDefaultCard(Long userId, Long cardId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        List<PaymentMethod> cards = paymentMethodRepository.findByUser(user);
        cards.forEach(c -> c.setDefault(c.getId().equals(cardId)));
        paymentMethodRepository.saveAll(cards);
    }

    public WalletAnalyticsDTO getSpendingAnalytics(User user) {
        List<Transaction> history = transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user);
        List<Transaction> outgoing = history.stream()
                .filter(t -> t.getSender() != null && t.getSender().getUserId().equals(user.getUserId()))
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .collect(Collectors.toList());

        BigDecimal totalSpent = outgoing.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> categories = outgoing.stream()
                .collect(Collectors.groupingBy(t -> t.getType().name(),
                        Collectors.mapping(Transaction::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        return new WalletAnalyticsDTO(totalSpent, categories, (long) outgoing.size());
    }
}