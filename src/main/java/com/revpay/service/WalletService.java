package com.revpay.service;

import com.revpay.exception.InsufficientBalanceException;
import com.revpay.exception.InvalidStatusException;
import com.revpay.exception.ResourceNotFoundException;
import com.revpay.exception.UnauthorizedException;
import com.revpay.model.entity.*;
import com.revpay.repository.*;
import com.revpay.util.NotificationUtil;
import com.revpay.model.dto.TransactionRequest;
import com.revpay.model.dto.TransferCompletedEvent;
import com.revpay.model.dto.WalletAnalyticsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PaymentMethodRepository paymentMethodRepository;
    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    private static final BigDecimal DAILY_LIMIT = new BigDecimal("50000.00");
    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("1000.00");

    // --- 1. HELPERS & SECURITY ---

    private String generateRef() {
        return "TXN-" + System.currentTimeMillis();
    }

    private void checkDailyLimit(User sender, BigDecimal newAmount) {
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.now().with(LocalTime.MAX);

        BigDecimal totalSentToday = transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(sender, sender)
                .stream()
                .filter(t -> t.getSender() != null && t.getSender().getUserId().equals(sender.getUserId()))
                .filter(t -> t.getType() == Transaction.TransactionType.SEND || t.getType() == Transaction.TransactionType.INVOICE_PAYMENT)
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .filter(t -> t.getTimestamp().isAfter(startOfDay) && t.getTimestamp().isBefore(endOfDay))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalSentToday.add(newAmount).compareTo(DAILY_LIMIT) > 0) {
            log.warn("SECURITY | LIMIT_EXCEEDED | User: {} | Used: {}", sender.getEmail(), totalSentToday);
            throw new IllegalStateException("Daily transfer limit of ₹50,000 exceeded!");
        }
    }

    private void checkAndAlertLowBalance(Wallet wallet) {
        if (wallet.getBalance().compareTo(LOW_BALANCE_THRESHOLD) < 0) {
            notificationService.createNotification(
                    wallet.getUser().getUserId(),
                    NotificationUtil.lowBalanceAlert(wallet.getBalance()),
                    "WALLET"
            );
            log.warn("Low balance alert sent to userId: {}", wallet.getUser().getUserId());
        }
    }

    // --- 2. TRANSACTION HISTORY ---

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionHistory(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getTransactionHistoryPaged(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user, pageable);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getMyHistory(User user, String type) {
        List<Transaction> all = transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user);
        if (type != null && !type.isEmpty()) {
            return all.stream()
                    .filter(t -> t.getType().name().equalsIgnoreCase(type))
                    .toList();
        }
        return all;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getMyHistoryPaged(User user, String type, Pageable pageable) {
        if (type != null && !type.isEmpty()) {
            try {
                Transaction.TransactionType enumType = Transaction.TransactionType.valueOf(type.toUpperCase());
                return transactionRepository.findByUserAndType(user.getUserId(), enumType, pageable);
            } catch (IllegalArgumentException e) {
                return Page.empty(pageable);
            }
        }
        return transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user, pageable);
    }

    // --- 3. MONEY REQUESTS ---

    @Transactional
    public Transaction requestMoney(Long requesterId, String targetEmail, BigDecimal amount) {
        User requester = userRepository.findById(requesterId).orElseThrow(() -> new ResourceNotFoundException("Requester not found"));
        User target = userRepository.findByEmail(targetEmail).orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        Transaction request = new Transaction();
        request.setSender(target);
        request.setReceiver(requester);
        request.setAmount(amount);
        request.setType(Transaction.TransactionType.REQUEST);
        request.setStatus(Transaction.TransactionStatus.PENDING);
        request.setTransactionRef(generateRef());
        request.setDescription("Money request from " + requester.getFullName());

        log.info("REQUEST | CREATED | From: {} | To: {}", requester.getEmail(), targetEmail);

        notificationService.createNotification(target.getUserId(), NotificationUtil.requestCreated(amount), "REQUEST");

        return transactionRepository.save(request);
    }

    @Transactional
    public Transaction acceptRequest(Long transactionId, String pin) {
        log.info("Accepting money request: transactionId={}", transactionId);

        Transaction request = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Money request not found with ID: " + transactionId));

        if (request.getStatus() != Transaction.TransactionStatus.PENDING) {
            throw new InvalidStatusException("This request cannot be accepted because it is " + request.getStatus());
        }

        TransactionRequest paymentReq = TransactionRequest.builder()
                .receiverIdentifier(request.getReceiver().getEmail())
                .amount(request.getAmount())
                .description("Fulfillment of Request #" + transactionId)
                .transactionPin(pin)
                .idempotencyKey("ACCPT-" + transactionId)
                .build();

        sendMoney(request.getSender().getUserId(), paymentReq);

        request.setStatus(Transaction.TransactionStatus.COMPLETED);
        Transaction updatedRequest = transactionRepository.save(request);

        notificationService.createNotification(
                request.getReceiver().getUserId(),
                NotificationUtil.requestAccepted(request.getAmount()),
                "REQUEST"
        );

        log.info("Request {} accepted and fulfilled successfully", transactionId);
        return updatedRequest;
    }

    @Transactional
    public Transaction declineRequest(Long userId, Long transactionId) {
        log.info("Declining money request transactionId: {} by userId: {}", transactionId, userId);

        Transaction request = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

        if (!request.getReceiver().getUserId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to decline this request");
        }

        if (request.getStatus() != Transaction.TransactionStatus.PENDING) {
            throw new InvalidStatusException("Only pending requests can be declined");
        }

        request.setStatus(Transaction.TransactionStatus.DECLINED);
        transactionRepository.save(request);

        notificationService.createNotification(
                request.getSender().getUserId(),
                NotificationUtil.requestDeclined(request.getAmount()),
                "REQUEST"
        );

        log.info("Request {} declined successfully", transactionId);
        return request;
    }

    @Transactional
    public Transaction cancelOutgoingRequest(Long userId, Long transactionId) {
        log.info("Cancelling outgoing request transactionId: {} by userId: {}", transactionId, userId);

        Transaction request = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

        if (!request.getSender().getUserId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to cancel this request");
        }

        if (request.getStatus() != Transaction.TransactionStatus.PENDING) {
            throw new InvalidStatusException("Only pending requests can be cancelled");
        }

        request.setStatus(Transaction.TransactionStatus.CANCELLED);
        transactionRepository.save(request);

        log.info("Outgoing request {} cancelled successfully", transactionId);
        return request;
    }

    @Transactional(readOnly = true)
    public List<Transaction> getIncomingRequests(Long userId) {
        return transactionRepository.findByReceiverUserIdAndTypeAndStatus(
                userId, Transaction.TransactionType.REQUEST, Transaction.TransactionStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getIncomingRequestsPaged(Long userId, Pageable pageable) {
        return transactionRepository.findByReceiverUserIdAndTypeAndStatus(
                userId, Transaction.TransactionType.REQUEST, Transaction.TransactionStatus.PENDING, pageable);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getOutgoingRequests(Long userId) {
        return transactionRepository.findBySenderUserIdAndTypeAndStatus(
                userId, Transaction.TransactionType.REQUEST, Transaction.TransactionStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getOutgoingRequestsPaged(Long userId, Pageable pageable) {
        return transactionRepository.findBySenderUserIdAndTypeAndStatus(
                userId, Transaction.TransactionType.REQUEST, Transaction.TransactionStatus.PENDING, pageable);
    }

    // --- 4. CORE WALLET OPERATIONS ---

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return walletRepository.findByUser(user)
                .map(Wallet::getBalance)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, PessimisticLockingFailureException.class, DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    @Transactional
    public Transaction sendMoney(Long senderId, TransactionRequest request) {
        User sender = userRepository.findById(senderId).orElseThrow(() -> new ResourceNotFoundException("Sender not found"));
        User receiver = userRepository.findByEmail(request.getReceiverIdentifier()).orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));

        if (senderId.equals(receiver.getUserId())) {
            throw new IllegalStateException("You cannot send money to yourself.");
        }

        if (!passwordEncoder.matches(request.getTransactionPin(), sender.getTransactionPinHash())) {
            throw new UnauthorizedException("Invalid Transaction PIN!");
        }

        checkDailyLimit(sender, request.getAmount());

        // --- DEADLOCK PREVENTION ALGORITHM ---
        Wallet senderWallet;
        Wallet receiverWallet;

        if (sender.getUserId() < receiver.getUserId()) {
            senderWallet = walletRepository.findByUserUserIdForUpdate(sender.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sender wallet not found"));
            receiverWallet = walletRepository.findByUserUserIdForUpdate(receiver.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Receiver wallet not found"));
        } else {
            receiverWallet = walletRepository.findByUserUserIdForUpdate(receiver.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Receiver wallet not found"));
            senderWallet = walletRepository.findByUserUserIdForUpdate(sender.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sender wallet not found"));
        }

        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance!");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(request.getAmount()));
        receiverWallet.setBalance(receiverWallet.getBalance().add(request.getAmount()));

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        checkAndAlertLowBalance(senderWallet);

        Transaction tx = new Transaction();
        tx.setSender(sender);
        tx.setReceiver(receiver);
        tx.setAmount(request.getAmount());
        tx.setType(Transaction.TransactionType.SEND);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setTransactionRef(generateRef());
        tx.setDescription(request.getDescription() != null ? request.getDescription() : "Transfer to " + request.getReceiverIdentifier());
        tx.setTimestamp(LocalDateTime.now()); // Ensure timestamp is set before saving

        // Save transaction FIRST
        Transaction savedTx = transactionRepository.save(tx);

        // Publish event for Async Notification processing using Builder to match your DTO perfectly
        eventPublisher.publishEvent(TransferCompletedEvent.builder()
                .transactionId(savedTx.getTransactionId())
                .senderId(sender.getUserId())
                .receiverId(receiver.getUserId())
                .amount(savedTx.getAmount())
                .senderName(sender.getFullName())
                .receiverName(receiver.getFullName())
                .currency("INR")
                .timestamp(savedTx.getTimestamp())
                .build());

        return savedTx;
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, PessimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    @Transactional
    public Transaction addFunds(Long userId, BigDecimal amount, String description) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Wallet wallet = walletRepository.findByUserUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setReceiver(user);
        tx.setAmount(amount);
        tx.setType(Transaction.TransactionType.ADD_FUNDS);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setTransactionRef(generateRef());
        tx.setDescription("Added via: " + description);

        notificationService.createNotification(userId, NotificationUtil.walletCredited(amount), "WALLET");
        return transactionRepository.save(tx);
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, PessimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    @Transactional
    public Transaction withdrawFunds(Long userId, BigDecimal amount) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Wallet wallet = walletRepository.findByUserUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        checkAndAlertLowBalance(wallet);

        Transaction tx = new Transaction();
        tx.setSender(user);
        tx.setAmount(amount);
        tx.setType(Transaction.TransactionType.WITHDRAW);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setTransactionRef(generateRef());
        tx.setDescription("Withdrawal to bank account");

        notificationService.createNotification(userId, NotificationUtil.walletDebited(amount), "WALLET");
        return transactionRepository.save(tx);
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, PessimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    @Transactional
    public Transaction payInvoice(Long userId, Long invoiceId, String pin) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(pin, user.getTransactionPinHash())) {
            throw new UnauthorizedException("Invalid PIN!");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw new InvalidStatusException("This invoice has already been paid.");
        }

        Wallet wallet = walletRepository.findByUserUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        if (wallet.getBalance().compareTo(invoice.getTotalAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance!");
        }

        // ADDED FIX: Ensure money goes into the business wallet
        Wallet businessWallet = walletRepository.findByUserUserIdForUpdate(invoice.getBusinessProfile().getUser().getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Business wallet not found"));

        wallet.setBalance(wallet.getBalance().subtract(invoice.getTotalAmount()));
        businessWallet.setBalance(businessWallet.getBalance().add(invoice.getTotalAmount()));

        walletRepository.save(wallet);
        walletRepository.save(businessWallet);

        checkAndAlertLowBalance(wallet);

        invoice.setStatus(Invoice.InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        Transaction tx = new Transaction();
        tx.setSender(user);
        tx.setReceiver(invoice.getBusinessProfile().getUser());
        tx.setAmount(invoice.getTotalAmount());
        tx.setType(Transaction.TransactionType.INVOICE_PAYMENT);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setTransactionRef(generateRef());
        tx.setDescription("Payment for Invoice #" + invoiceId);

        notificationService.createNotification(userId, NotificationUtil.invoicePaid(invoice.getTotalAmount()), "INVOICE");
        return transactionRepository.save(tx);
    }

    // --- 5. CARD MANAGEMENT ---

    @Transactional
    public PaymentMethod addCard(Long userId, PaymentMethod card) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean exists = paymentMethodRepository.findByUser(user).stream()
                .anyMatch(c -> c.getCardNumber().equals(card.getCardNumber()));
        if (exists) throw new IllegalStateException("This card is already linked to your account.");

        card.setUser(user);

        notificationService.createNotification(userId, NotificationUtil.cardAdded(), "SECURITY");
        return paymentMethodRepository.save(card);
    }

    @Transactional
    public PaymentMethod updateCard(Long userId, Long cardId, PaymentMethod updatedCard) {
        log.info("Updating card cardId: {} for userId: {}", cardId, userId);

        PaymentMethod existingCard = paymentMethodRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));

        if (!existingCard.getUser().getUserId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to update this card");
        }

        if (updatedCard.getExpiryDate() != null) {
            existingCard.setExpiryDate(updatedCard.getExpiryDate());
        }
        if (updatedCard.getBillingAddress() != null) {
            existingCard.setBillingAddress(updatedCard.getBillingAddress());
        }

        paymentMethodRepository.save(existingCard);

        notificationService.createNotification(userId, NotificationUtil.cardUpdated(), "SECURITY");

        log.info("Card {} updated successfully", cardId);
        return existingCard;
    }

    @Transactional
    public void deleteCard(Long userId, Long cardId) {
        PaymentMethod card = paymentMethodRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));

        if (!card.getUser().getUserId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to delete this card.");
        }

        paymentMethodRepository.delete(card);

        notificationService.createNotification(userId, NotificationUtil.cardDeleted(), "SECURITY");
    }

    @Transactional(readOnly = true)
    public List<PaymentMethod> getCards(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return paymentMethodRepository.findByUser(user);
    }

    @Transactional(readOnly = true)
    public Page<PaymentMethod> getCardsPaged(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return paymentMethodRepository.findByUser(user, pageable);
    }

    @Transactional
    public void setDefaultCard(Long userId, Long cardId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<PaymentMethod> oldDefaults = paymentMethodRepository.findByUserAndIsDefault(user, true);
        oldDefaults.forEach(c -> c.setDefault(false));
        paymentMethodRepository.saveAll(oldDefaults);

        PaymentMethod newDefault = paymentMethodRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));

        if (!newDefault.getUser().getUserId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to modify this card");
        }

        newDefault.setDefault(true);
        paymentMethodRepository.save(newDefault);
    }

    // --- 6. FILTERING & SEARCH METHODS ---

    @Transactional(readOnly = true)
    public List<Transaction> filterTransactions(Long userId, String type, LocalDateTime startDate, LocalDateTime endDate, String status, BigDecimal minAmount, BigDecimal maxAmount) {
        List<Transaction> transactions = getTransactionHistory(userId);

        if (type != null && !type.isEmpty()) {
            transactions = transactions.stream().filter(t -> t.getType().name().equalsIgnoreCase(type)).collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty()) {
            transactions = transactions.stream().filter(t -> t.getStatus().name().equalsIgnoreCase(status)).collect(Collectors.toList());
        }
        if (startDate != null) {
            transactions = transactions.stream().filter(t -> t.getTimestamp().isAfter(startDate)).collect(Collectors.toList());
        }
        if (endDate != null) {
            transactions = transactions.stream().filter(t -> t.getTimestamp().isBefore(endDate)).collect(Collectors.toList());
        }
        if (minAmount != null) {
            transactions = transactions.stream().filter(t -> t.getAmount().compareTo(minAmount) >= 0).collect(Collectors.toList());
        }
        if (maxAmount != null) {
            transactions = transactions.stream().filter(t -> t.getAmount().compareTo(maxAmount) <= 0).collect(Collectors.toList());
        }
        return transactions;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> filterTransactionsPaged(Long userId, String type, LocalDateTime startDate, LocalDateTime endDate, String status, BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable) {
        List<Transaction> filtered = filterTransactions(userId, type, startDate, endDate, status, minAmount, maxAmount);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());
        List<Transaction> subList = start <= end ? filtered.subList(start, end) : List.of();

        return new PageImpl<>(subList, pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public List<Transaction> searchTransactions(Long userId, String keyword) {
        List<Transaction> allTransactions = getTransactionHistory(userId);
        return allTransactions.stream()
                .filter(t ->
                        (t.getTransactionRef() != null && t.getTransactionRef().contains(keyword)) ||
                                (t.getSender() != null && t.getSender().getFullName() != null && t.getSender().getFullName().toLowerCase().contains(keyword.toLowerCase())) ||
                                (t.getReceiver() != null && t.getReceiver().getFullName() != null && t.getReceiver().getFullName().toLowerCase().contains(keyword.toLowerCase())) ||
                                (t.getDescription() != null && t.getDescription().toLowerCase().contains(keyword.toLowerCase()))
                )
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<Transaction> searchTransactionsPaged(Long userId, String keyword, Pageable pageable) {
        List<Transaction> searched = searchTransactions(userId, keyword);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), searched.size());
        List<Transaction> subList = start <= end ? searched.subList(start, end) : List.of();

        return new PageImpl<>(subList, pageable, searched.size());
    }

    // --- 7. EXPORT METHODS ---

    @Transactional(readOnly = true)
    public String exportTransactionsToCSV(Long userId) {
        List<Transaction> transactions = getTransactionHistory(userId);
        StringBuilder csv = new StringBuilder();

        csv.append("Transaction ID,Date,Type,Amount,Status,Sender,Receiver,Description\n");
        for (Transaction t : transactions) {
            csv.append(t.getTransactionRef()).append(",");
            csv.append(t.getTimestamp()).append(",");
            csv.append(t.getType()).append(",");
            csv.append(t.getAmount()).append(",");
            csv.append(t.getStatus()).append(",");
            csv.append(t.getSender() != null ? t.getSender().getFullName() : "N/A").append(",");
            csv.append(t.getReceiver() != null ? t.getReceiver().getFullName() : "N/A").append(",");
            csv.append(t.getDescription() != null ? t.getDescription().replace(",", " ") : "").append("\n");
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public byte[] exportTransactionsToPDF(Long userId) {
        return exportTransactionsToCSV(userId).getBytes();
    }

    // --- 8. ANALYTICS ---

    @Transactional(readOnly = true)
    public WalletAnalyticsDTO getSpendingAnalytics(User user) {
        log.info("Generating spending analytics for userId: {}", user.getUserId());

        List<Transaction> history = transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user);

        List<Transaction> outgoing = history.stream()
                .filter(t -> t.getSender() != null && t.getSender().getUserId().equals(user.getUserId()))
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .toList();

        BigDecimal totalSpent = outgoing.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> categories = outgoing.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getType().name(),
                        Collectors.mapping(Transaction::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));

        BigDecimal currentBalance = walletRepository.findByUser(user)
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO);

        BigDecimal averageTransactionValue = outgoing.isEmpty() ? BigDecimal.ZERO :
                totalSpent.divide(BigDecimal.valueOf(outgoing.size()), 2, java.math.RoundingMode.HALF_UP);

        return WalletAnalyticsDTO.builder()
                .currentBalance(currentBalance)
                .totalSpent(totalSpent)
                .spendingByCategory(categories)
                .transactionCount((long) outgoing.size())
                .averageTransactionValue(averageTransactionValue)
                .monthlyChangePercentage(0.0)
                .currency("INR")
                .build();
    }

    // --- 9. LOAN WALLET INTEGRATIONS ---

    @Retryable(
            retryFor = {CannotAcquireLockException.class, PessimisticLockingFailureException.class, DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    @Transactional
    public Transaction addFundsForLoan(Long userId, BigDecimal amount, String description) {
        Wallet wallet = walletRepository.findByUserUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setReceiver(wallet.getUser());
        tx.setAmount(amount);
        tx.setType(Transaction.TransactionType.LOAN_DISBURSEMENT);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setDescription(description);
        tx.setTransactionRef(generateRef());

        return transactionRepository.save(tx);
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, PessimisticLockingFailureException.class, DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    @Transactional
    public Transaction withdrawFundsForLoan(Long userId, BigDecimal amount, String description) {
        Wallet wallet = walletRepository.findByUserUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for loan repayment");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setSender(wallet.getUser());
        tx.setAmount(amount);
        tx.setType(Transaction.TransactionType.LOAN_REPAYMENT);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setDescription(description);
        tx.setTransactionRef(generateRef());

        return transactionRepository.save(tx);
    }
}