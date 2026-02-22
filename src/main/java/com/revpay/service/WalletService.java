package com.revpay.service;

import com.revpay.model.entity.*;
import com.revpay.repository.*;
import com.revpay.util.NotificationUtil;
import com.revpay.model.dto.TransactionRequest;
import com.revpay.model.dto.WalletAnalyticsDTO;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WalletService {

    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);

    @Autowired private WalletRepository walletRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private NotificationService notificationService;

    // --- 1. HELPERS & SECURITY ---

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
                .filter(t -> t.getType() == Transaction.TransactionType.SEND || t.getType() == Transaction.TransactionType.INVOICE_PAYMENT)
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .filter(t -> t.getTimestamp().isAfter(startOfDay) && t.getTimestamp().isBefore(endOfDay))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalSentToday.add(newAmount).compareTo(dailyLimit) > 0) {
            logger.warn("SECURITY | LIMIT_EXCEEDED | User: {} | Used: {}", sender.getEmail(), totalSentToday);
            throw new RuntimeException("Daily transfer limit of ₹50,000 exceeded!");
        }
    }

    private void checkAndAlertLowBalance(Wallet wallet) {
        BigDecimal lowBalanceThreshold = new BigDecimal("1000"); // ₹1000
        if (wallet.getBalance().compareTo(lowBalanceThreshold) < 0) {
            notificationService.createNotification(
                    wallet.getUser().getUserId(),
                    NotificationUtil.lowBalanceAlert(wallet.getBalance()),
                    "WALLET"
            );
            logger.warn("Low balance alert sent to userId: {}", wallet.getUser().getUserId());
        }
    }

    // --- 2. TRANSACTION HISTORY ---

    public List<Transaction> getTransactionHistory(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user);
    }

    public List<Transaction> getMyHistory(User user, String type) {
        List<Transaction> all = transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user);
        if (type != null && !type.isEmpty()) {
            return all.stream()
                    .filter(t -> t.getType().name().equalsIgnoreCase(type))
                    .toList();
        }
        return all;
    }

    // --- 3. MONEY REQUESTS (UPDATED) ---

    @Transactional
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
        request.setDescription("Money request from " + requester.getFullName());

        logger.info("REQUEST | CREATED | From: {} | To: {}", requester.getEmail(), targetEmail);

        notificationService.createNotification(target.getUserId(), NotificationUtil.requestCreated(amount), "REQUEST");

        return transactionRepository.save(request);
    }

    @Transactional
    public Transaction acceptRequest(Long transactionId, String pin) {
        Transaction request = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // Verify the request is still pending
        if (request.getStatus() != Transaction.TransactionStatus.PENDING) {
            throw new RuntimeException("This request is no longer pending");
        }

        TransactionRequest paymentReq = new TransactionRequest(
                request.getReceiver().getEmail(),
                request.getAmount(),
                "Payment for Request",
                pin
        );

        sendMoney(request.getSender().getUserId(), paymentReq);

        request.setStatus(Transaction.TransactionStatus.COMPLETED);

        notificationService.createNotification(
                request.getReceiver().getUserId(),
                NotificationUtil.requestAccepted(request.getAmount()),
                "REQUEST"
        );

        return transactionRepository.save(request);
    }

    @Transactional
    public Transaction declineRequest(Long userId, Long transactionId) {
        logger.info("Declining money request transactionId: {} by userId: {}", transactionId, userId);

        Transaction request = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // Verify this request is for this user (user is the receiver)
        if (!request.getReceiver().getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized to decline this request");
        }

        // Verify the request is still pending
        if (request.getStatus() != Transaction.TransactionStatus.PENDING) {
            throw new RuntimeException("Only pending requests can be declined");
        }

        request.setStatus(Transaction.TransactionStatus.DECLINED);
        transactionRepository.save(request);

        // Notify the sender
        notificationService.createNotification(
                request.getSender().getUserId(),
                NotificationUtil.requestDeclined(request.getAmount()),
                "REQUEST"
        );

        logger.info("Request {} declined successfully", transactionId);
        return request;
    }

    @Transactional
    public Transaction cancelOutgoingRequest(Long userId, Long transactionId) {
        logger.info("Cancelling outgoing request transactionId: {} by userId: {}", transactionId, userId);

        Transaction request = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Request not found"));


        if (!request.getSender().getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized to cancel this request");
        }


        if (request.getStatus() != Transaction.TransactionStatus.PENDING) {
            throw new RuntimeException("Only pending requests can be cancelled");
        }

        request.setStatus(Transaction.TransactionStatus.CANCELLED);
        transactionRepository.save(request);

        logger.info("Outgoing request {} cancelled successfully", transactionId);
        return request;
    }

    public List<Transaction> getIncomingRequests(Long userId) {
        logger.info("Fetching incoming money requests for userId: {}", userId);

        return transactionRepository.findByReceiverUserIdAndTypeAndStatus(
                userId,
                Transaction.TransactionType.REQUEST,
                Transaction.TransactionStatus.PENDING
        );
    }

    public List<Transaction> getOutgoingRequests(Long userId) {
        logger.info("Fetching outgoing money requests for userId: {}", userId);

        return transactionRepository.findBySenderUserIdAndTypeAndStatus(
                userId,
                Transaction.TransactionType.REQUEST,
                Transaction.TransactionStatus.PENDING
        );
    }

    // --- 4. CORE WALLET OPERATIONS (UPDATED WITH LOW BALANCE CHECK) ---

    public BigDecimal getBalance(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        return walletRepository.findByUser(user)
                .map(Wallet::getBalance)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
    }

    @Transactional
    public Transaction sendMoney(Long senderId, TransactionRequest request) {
        User sender = userRepository.findById(senderId).orElseThrow(() -> new RuntimeException("Sender not found"));
        User receiver = userRepository.findByEmail(request.getReceiverIdentifier()).orElseThrow(() -> new RuntimeException("Receiver not found"));

        if (!passwordEncoder.matches(request.getTransactionPin(), sender.getTransactionPinHash())) {
            throw new RuntimeException("Invalid Transaction PIN!");
        }

        checkDailyLimit(sender, request.getAmount());

        Wallet senderWallet = walletRepository.findByUser(sender).orElseThrow(() -> new RuntimeException("Sender wallet not found"));
        Wallet receiverWallet = walletRepository.findByUser(receiver).orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient balance!");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(request.getAmount()));
        receiverWallet.setBalance(receiverWallet.getBalance().add(request.getAmount()));

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        // Check for low balance after transaction
        checkAndAlertLowBalance(senderWallet);

        Transaction tx = new Transaction();
        tx.setSender(sender);
        tx.setReceiver(receiver);
        tx.setAmount(request.getAmount());
        tx.setType(Transaction.TransactionType.SEND);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setTransactionRef(generateRef());
        tx.setDescription("Transfer to " + request.getReceiverIdentifier());

        notificationService.createNotification(senderId, NotificationUtil.moneySent(request.getAmount()), "TRANSFER");
        notificationService.createNotification(receiver.getUserId(), NotificationUtil.moneyReceived(request.getAmount()), "TRANSFER");

        return transactionRepository.save(tx);
    }

    @Transactional
    public Transaction addFunds(Long userId, BigDecimal amount, String description) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Wallet wallet = walletRepository.findByUser(user).orElseThrow(() -> new RuntimeException("Wallet not found"));

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

    @Transactional
    public Transaction withdrawFunds(Long userId, BigDecimal amount) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Wallet wallet = walletRepository.findByUser(user).orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getBalance().compareTo(amount) < 0) throw new RuntimeException("Insufficient balance");

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        // Check for low balance after withdrawal
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

    @Transactional
    public Transaction payInvoice(Long userId, Long invoiceId, String pin) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(pin, user.getTransactionPinHash())) throw new RuntimeException("Invalid PIN!");

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new RuntimeException("Invoice not found"));
        Wallet wallet = walletRepository.findByUser(user).orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getBalance().compareTo(invoice.getTotalAmount()) < 0) throw new RuntimeException("Insufficient balance!");

        wallet.setBalance(wallet.getBalance().subtract(invoice.getTotalAmount()));
        walletRepository.save(wallet);

        // Check for low balance after payment
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

    // --- 5. CARD MANAGEMENT (UPDATED) ---

    public PaymentMethod addCard(Long userId, PaymentMethod card) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        boolean exists = paymentMethodRepository.findByUser(user).stream()
                .anyMatch(c -> c.getCardNumber().equals(card.getCardNumber()));
        if (exists) throw new RuntimeException("This card is already linked to your account.");

        card.setUser(user);

        notificationService.createNotification(userId, NotificationUtil.cardAdded(), "SECURITY");
        return paymentMethodRepository.save(card);
    }

    @Transactional
    public PaymentMethod updateCard(Long userId, Long cardId, PaymentMethod updatedCard) {
        logger.info("Updating card cardId: {} for userId: {}", cardId, userId);

        PaymentMethod existingCard = paymentMethodRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found"));

        if (!existingCard.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized to update this card");
        }

        if (updatedCard.getExpiryDate() != null) {
            existingCard.setExpiryDate(updatedCard.getExpiryDate());
        }
        if (updatedCard.getBillingAddress() != null) {
            existingCard.setBillingAddress(updatedCard.getBillingAddress());
        }
        // Card number and CVV should not be updatable for security reasons

        paymentMethodRepository.save(existingCard);

        notificationService.createNotification(
                userId,
                NotificationUtil.cardUpdated(),
                "SECURITY"
        );

        logger.info("Card {} updated successfully", cardId);
        return existingCard;
    }

    @Transactional
    public void deleteCard(Long userId, Long cardId) {
        PaymentMethod card = paymentMethodRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found"));

        if (!card.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("You are not authorized to delete this card.");
        }

        paymentMethodRepository.delete(card);

        notificationService.createNotification(userId, NotificationUtil.cardDeleted(), "SECURITY");
    }

    public List<PaymentMethod> getCards(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return paymentMethodRepository.findByUser(user);
    }

    @Transactional
    public void setDefaultCard(Long userId, Long cardId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        List<PaymentMethod> oldDefaults = paymentMethodRepository.findByUserAndIsDefault(user, true);
        oldDefaults.forEach(c -> c.setDefault(false));
        paymentMethodRepository.saveAll(oldDefaults);

        PaymentMethod newDefault = paymentMethodRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found"));
        newDefault.setDefault(true);
        paymentMethodRepository.save(newDefault);
    }

    // --- 6. NEW: FILTERING & SEARCH METHODS ---

    public List<Transaction> filterTransactions(
            Long userId,
            String type,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String status,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
        logger.info("Filtering transactions for userId: {} with filters", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Transaction> transactions = transactionRepository
                .findBySenderOrReceiverOrderByTimestampDesc(user, user);

        if (type != null && !type.isEmpty()) {
            transactions = transactions.stream()
                    .filter(t -> t.getType().name().equalsIgnoreCase(type))
                    .collect(Collectors.toList());
        }

        if (status != null && !status.isEmpty()) {
            transactions = transactions.stream()
                    .filter(t -> t.getStatus().name().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        if (startDate != null) {
            transactions = transactions.stream()
                    .filter(t -> t.getTimestamp().isAfter(startDate))
                    .collect(Collectors.toList());
        }

        if (endDate != null) {
            transactions = transactions.stream()
                    .filter(t -> t.getTimestamp().isBefore(endDate))
                    .collect(Collectors.toList());
        }

        if (minAmount != null) {
            transactions = transactions.stream()
                    .filter(t -> t.getAmount().compareTo(minAmount) >= 0)
                    .collect(Collectors.toList());
        }

        if (maxAmount != null) {
            transactions = transactions.stream()
                    .filter(t -> t.getAmount().compareTo(maxAmount) <= 0)
                    .collect(Collectors.toList());
        }

        logger.info("Found {} transactions after filtering", transactions.size());
        return transactions;
    }

    public List<Transaction> searchTransactions(Long userId, String keyword) {
        logger.info("Searching transactions for userId: {} with keyword: {}", userId, keyword);

        List<Transaction> allTransactions = getTransactionHistory(userId);

        return allTransactions.stream()
                .filter(t ->
                        (t.getTransactionRef() != null && t.getTransactionRef().contains(keyword)) ||
                                (t.getSender() != null && t.getSender().getFullName() != null &&
                                        t.getSender().getFullName().toLowerCase().contains(keyword.toLowerCase())) ||
                                (t.getReceiver() != null && t.getReceiver().getFullName() != null &&
                                        t.getReceiver().getFullName().toLowerCase().contains(keyword.toLowerCase())) ||
                                (t.getDescription() != null && t.getDescription().toLowerCase().contains(keyword.toLowerCase()))
                )
                .collect(Collectors.toList());
    }

    // --- 7. NEW: EXPORT METHODS ---

    public String exportTransactionsToCSV(Long userId) {
        logger.info("Exporting transactions to CSV for userId: {}", userId);

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

    public byte[] exportTransactionsToPDF(Long userId) {
        logger.info("Exporting transactions to PDF for userId: {}", userId);
        // For now, return CSV as bytes. In production, use a PDF library like iText
        return exportTransactionsToCSV(userId).getBytes();
    }

    // --- 8. ANALYTICS

    public WalletAnalyticsDTO getSpendingAnalytics(User user) {
        List<Transaction> history = transactionRepository.findBySenderOrReceiverOrderByTimestampDesc(user, user);
        List<Transaction> outgoing = history.stream()
                .filter(t -> t.getSender() != null && t.getSender().getUserId().equals(user.getUserId()))
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .toList();

        BigDecimal totalSpent = outgoing.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> categories = outgoing.stream()
                .collect(Collectors.groupingBy(t -> t.getType().name(),
                        Collectors.mapping(Transaction::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        return new WalletAnalyticsDTO(totalSpent, categories, (long) outgoing.size());
    }
}