package com.revpay.service;

import com.revpay.exception.*;
import com.revpay.model.dto.TransactionRequest;
import com.revpay.model.dto.TransferCompletedEvent;
import com.revpay.model.dto.WalletAnalyticsDTO;
import com.revpay.model.entity.*;
import com.revpay.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

    @Mock private WalletRepository walletRepo;
    @Mock private TransactionRepository transRepo;
    @Mock private UserRepository userRepo;
    @Mock private PasswordEncoder encoder;
    @Mock private NotificationService notificationService;
    @Mock private PaymentMethodRepository paymentMethodRepo;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WalletService walletService;

    private User sender;
    private User receiver;
    private Wallet senderWallet;
    private Wallet receiverWallet;
    private PaymentMethod card;

    @BeforeEach
    void setup() {
        sender = new User();
        sender.setUserId(1L);
        sender.setEmail("sender@revpay.com");
        sender.setTransactionPinHash("hashed_1234");
        sender.setFullName("Sender Name");

        senderWallet = new Wallet();
        senderWallet.setBalance(new BigDecimal("5000.00"));
        senderWallet.setUser(sender);

        receiver = new User();
        receiver.setUserId(2L);
        receiver.setEmail("receiver@revpay.com");
        receiver.setFullName("Receiver Name");

        receiverWallet = new Wallet();
        receiverWallet.setBalance(new BigDecimal("1000.00"));
        receiverWallet.setUser(receiver);

        card = new PaymentMethod();
        card.setId(1L);
        card.setUser(sender);
        card.setCardNumber("**** **** **** 1234");
        card.setExpiryDate("12/25");
    }

    // ==================== CORE TRANSFER TESTS ====================

    @Test
    void testProcessTransfer_Success() {
        TransactionRequest req = TransactionRequest.builder()
                .receiverIdentifier("receiver@revpay.com")
                .amount(new BigDecimal("200.00"))
                .description("Lunch")
                .transactionPin("1234")
                .idempotencyKey("TEST-ID-123")
                .build();

        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepo.findByEmail("receiver@revpay.com")).thenReturn(Optional.of(receiver));
        when(encoder.matches("1234", sender.getTransactionPinHash())).thenReturn(true);

        when(walletRepo.findByUserUserIdForUpdate(sender.getUserId())).thenReturn(Optional.of(senderWallet));
        when(walletRepo.findByUserUserIdForUpdate(receiver.getUserId())).thenReturn(Optional.of(receiverWallet));

        lenient().when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());
        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.sendMoney(1L, req);

        assertEquals(new BigDecimal("4800.00"), senderWallet.getBalance());
        assertEquals(new BigDecimal("1200.00"), receiverWallet.getBalance());
        assertNotNull(result);

        verify(eventPublisher, times(1)).publishEvent(any(TransferCompletedEvent.class));
    }

    @Test
    void testProcessTransfer_WithInvalidPin_ThrowsException() {
        TransactionRequest req = TransactionRequest.builder()
                .receiverIdentifier("receiver@revpay.com")
                .amount(new BigDecimal("100.00"))
                .description("test")
                .transactionPin("wrong_pin")
                .idempotencyKey("TEST-ID-INVALID-PIN")
                .build();

        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        lenient().when(userRepo.findByEmail("receiver@revpay.com")).thenReturn(Optional.of(receiver));
        lenient().when(encoder.matches("wrong_pin", sender.getTransactionPinHash())).thenReturn(false);

        // FIXED: Expecting our custom UnauthorizedException
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> walletService.sendMoney(1L, req));

        assertTrue(ex.getMessage().toLowerCase().contains("pin"));
    }

    @Test
    void testProcessTransfer_WithInsufficientBalance_ThrowsException() {
        TransactionRequest req = TransactionRequest.builder()
                .receiverIdentifier("receiver@revpay.com")
                .amount(new BigDecimal("6000.00"))
                .transactionPin("1234")
                .build();

        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        lenient().when(userRepo.findByEmail("receiver@revpay.com")).thenReturn(Optional.of(receiver));
        lenient().when(encoder.matches("1234", sender.getTransactionPinHash())).thenReturn(true);

        lenient().when(walletRepo.findByUserUserIdForUpdate(sender.getUserId())).thenReturn(Optional.of(senderWallet));
        lenient().when(walletRepo.findByUserUserIdForUpdate(receiver.getUserId())).thenReturn(Optional.of(receiverWallet));
        lenient().when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        assertThrows(InsufficientBalanceException.class, () -> walletService.sendMoney(1L, req));
    }

    @Test
    void testProcessTransfer_ExceedingDailyLimit_ThrowsException() {
        TransactionRequest req = TransactionRequest.builder()
                .receiverIdentifier("receiver@revpay.com")
                .amount(new BigDecimal("50001.00"))
                .transactionPin("1234")
                .build();

        senderWallet.setBalance(new BigDecimal("100000.00"));

        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        lenient().when(userRepo.findByEmail("receiver@revpay.com")).thenReturn(Optional.of(receiver));
        lenient().when(encoder.matches("1234", sender.getTransactionPinHash())).thenReturn(true);
        lenient().when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        // FIXED: Expecting IllegalStateException as implemented in WalletService
        assertThrows(IllegalStateException.class, () -> walletService.sendMoney(1L, req));
    }

    // ==================== WALLET FUNDING TESTS ====================

    @Test
    void testAddFunds_Success() {
        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(walletRepo.findByUserUserIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.addFunds(1L, new BigDecimal("500.00"), "Credit Card");

        assertEquals(new BigDecimal("5500.00"), senderWallet.getBalance());
        assertNotNull(result);
        verify(notificationService).createNotification(eq(1L), anyString(), eq("WALLET"));
    }

    @Test
    void testWithdrawFunds_Success() {
        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(walletRepo.findByUserUserIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.withdrawFunds(1L, new BigDecimal("300.00"));

        assertEquals(new BigDecimal("4700.00"), senderWallet.getBalance());
        assertNotNull(result);
        verify(notificationService).createNotification(eq(1L), anyString(), eq("WALLET"));
    }

    @Test
    void testWithdrawFunds_WithInsufficientBalance_ThrowsException() {
        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(walletRepo.findByUserUserIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));

        InsufficientBalanceException ex = assertThrows(InsufficientBalanceException.class,
                () -> walletService.withdrawFunds(1L, new BigDecimal("6000.00")));
        assertTrue(ex.getMessage().toLowerCase().contains("insufficient"));
    }

    @Test
    void testGetBalance_ReturnsCorrectAmount() {
        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        // FIXED: Updated mock to match the new findByUser call in WalletService.getBalance
        when(walletRepo.findByUser(sender)).thenReturn(Optional.of(senderWallet));

        BigDecimal balance = walletService.getBalance(1L);

        assertEquals(new BigDecimal("5000.00"), balance);
    }

    // ==================== MONEY REQUESTS TESTS ====================

    @Test
    void testRequestMoney_CreatesPendingRequest() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepo.findByEmail("receiver@revpay.com")).thenReturn(Optional.of(receiver));
        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.requestMoney(1L, "receiver@revpay.com", new BigDecimal("300.00"));

        assertNotNull(result);
        assertEquals(Transaction.TransactionType.REQUEST, result.getType());
        assertEquals(Transaction.TransactionStatus.PENDING, result.getStatus());
        verify(notificationService).createNotification(eq(receiver.getUserId()), anyString(), eq("REQUEST"));
    }

    @Test
    void testAcceptRequest_CompletesTransaction() {
        Transaction pendingRequest = new Transaction();
        pendingRequest.setTransactionId(100L);
        pendingRequest.setSender(receiver); // Receiver of funds
        pendingRequest.setReceiver(sender); // Payer of funds
        pendingRequest.setAmount(new BigDecimal("200.00"));
        pendingRequest.setType(Transaction.TransactionType.REQUEST);
        pendingRequest.setStatus(Transaction.TransactionStatus.PENDING);

        when(transRepo.findById(100L)).thenReturn(Optional.of(pendingRequest));

        // Mocks for processTransfer side-effects
        lenient().when(userRepo.findById(receiver.getUserId())).thenReturn(Optional.of(receiver));
        lenient().when(userRepo.findByEmail(sender.getEmail())).thenReturn(Optional.of(sender));
        lenient().when(encoder.matches("1234", receiver.getTransactionPinHash())).thenReturn(true);
        lenient().when(walletRepo.findByUserUserIdForUpdate(receiver.getUserId())).thenReturn(Optional.of(receiverWallet));
        lenient().when(walletRepo.findByUserUserIdForUpdate(sender.getUserId())).thenReturn(Optional.of(senderWallet));
        lenient().when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());
        lenient().when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.acceptRequest(100L, "1234");

        assertEquals(Transaction.TransactionStatus.COMPLETED, result.getStatus());
    }

    @Test
    void testDeclineRequest_MarksAsDeclined() {
        Transaction pendingRequest = new Transaction();
        pendingRequest.setTransactionId(100L);
        pendingRequest.setSender(receiver);
        pendingRequest.setReceiver(sender);
        pendingRequest.setAmount(new BigDecimal("200.00"));
        pendingRequest.setType(Transaction.TransactionType.REQUEST);
        pendingRequest.setStatus(Transaction.TransactionStatus.PENDING);

        when(transRepo.findById(100L)).thenReturn(Optional.of(pendingRequest));
        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.declineRequest(sender.getUserId(), 100L);

        assertEquals(Transaction.TransactionStatus.DECLINED, result.getStatus());
    }

    @Test
    void testCancelOutgoingRequest_MarksAsCancelled() {
        Transaction pendingRequest = new Transaction();
        pendingRequest.setTransactionId(100L);
        pendingRequest.setSender(sender);
        pendingRequest.setReceiver(receiver);
        pendingRequest.setAmount(new BigDecimal("200.00"));
        pendingRequest.setType(Transaction.TransactionType.REQUEST);
        pendingRequest.setStatus(Transaction.TransactionStatus.PENDING);

        when(transRepo.findById(100L)).thenReturn(Optional.of(pendingRequest));
        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.cancelOutgoingRequest(sender.getUserId(), 100L);

        assertEquals(Transaction.TransactionStatus.CANCELLED, result.getStatus());
    }

    // ==================== CARD MANAGEMENT TESTS ====================

    @Test
    void testAddCard_SuccessfullyAddsCard() {
        PaymentMethod newCard = new PaymentMethod();
        newCard.setCardNumber("**** **** **** 5678");
        newCard.setExpiryDate("12/26");

        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        lenient().when(paymentMethodRepo.findByUser(sender)).thenReturn(new ArrayList<>());
        lenient().when(paymentMethodRepo.save(any(PaymentMethod.class))).thenReturn(newCard);

        PaymentMethod result = walletService.addCard(1L, newCard);

        assertNotNull(result);
        verify(notificationService).createNotification(eq(1L), anyString(), eq("SECURITY"));
    }

    @Test
    void testDeleteCard_RemovesCard() {
        lenient().when(paymentMethodRepo.findById(1L)).thenReturn(Optional.of(card));

        walletService.deleteCard(sender.getUserId(), 1L);

        verify(paymentMethodRepo).delete(card);
        verify(notificationService).createNotification(eq(1L), anyString(), eq("SECURITY"));
    }

    // ==================== ANALYTICS TESTS ====================

    @Test
    void testGetSpendingAnalytics_ReturnsCorrectMetrics() {
        Transaction tx = new Transaction();
        tx.setSender(sender);
        tx.setAmount(new BigDecimal("200.00"));
        tx.setType(Transaction.TransactionType.SEND);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);

        lenient().when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(sender, sender))
                .thenReturn(Collections.singletonList(tx));

        // Mock the repository call added in the updated analytics method
        lenient().when(walletRepo.findByUser(sender)).thenReturn(Optional.of(senderWallet));

        WalletAnalyticsDTO result = walletService.getSpendingAnalytics(sender);

        assertNotNull(result);
        assertEquals(new BigDecimal("200.00"), result.getTotalSpent());
        assertEquals(1, result.getTransactionCount());
        assertEquals(new BigDecimal("5000.00"), result.getCurrentBalance());
    }
}