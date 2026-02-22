package com.revpay.service;

import com.revpay.model.dto.TransactionRequest;
import com.revpay.model.dto.WalletAnalyticsDTO;  // Add this import
import com.revpay.model.entity.*;
import com.revpay.repository.*;
import com.revpay.service.NotificationService;
import com.revpay.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Collections;  // Add this for singletonList
import java.util.Optional;
import java.util.ArrayList;

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

    @InjectMocks
    private WalletService walletService;

    private User sender;
    private User receiver;
    private Wallet senderWallet;
    private Wallet receiverWallet;
    private PaymentMethod card;  // Add this field

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

        // Initialize card
        card = new PaymentMethod();
        card.setId(1L);
        card.setUser(sender);
        card.setCardNumber("**** **** **** 1234");
        card.setExpiryDate("12/25");
    }


    @Test
    void testSendMoney_Success() {
        TransactionRequest req = new TransactionRequest("receiver@revpay.com", new BigDecimal("200.00"), "Lunch", "1234");

        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepo.findByEmail("receiver@revpay.com")).thenReturn(Optional.of(receiver));
        when(encoder.matches("1234", sender.getTransactionPinHash())).thenReturn(true);
        when(walletRepo.findByUser(sender)).thenReturn(Optional.of(senderWallet));
        when(walletRepo.findByUser(receiver)).thenReturn(Optional.of(receiverWallet));
        when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());
        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.sendMoney(1L, req);

        assertEquals(new BigDecimal("4800.00"), senderWallet.getBalance());
        assertEquals(new BigDecimal("1200.00"), receiverWallet.getBalance());
        assertNotNull(result);
        verify(notificationService, times(2)).createNotification(anyLong(), anyString(), anyString());
    }

    @Test
    void testSendMoney_WithInvalidPin_ThrowsException() {
        TransactionRequest req = new TransactionRequest("receiver@revpay.com", new BigDecimal("100.00"), "test", "wrong_pin");

        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepo.findByEmail("receiver@revpay.com")).thenReturn(Optional.of(receiver));
        when(encoder.matches("wrong_pin", sender.getTransactionPinHash())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> walletService.sendMoney(1L, req));
        assertEquals("Invalid Transaction PIN!", ex.getMessage());
    }

    @Test
    void testSendMoney_WithInsufficientBalance_ThrowsException() {
        TransactionRequest req = new TransactionRequest("receiver@revpay.com", new BigDecimal("6000.00"), "broke", "1234");

        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepo.findByEmail("receiver@revpay.com")).thenReturn(Optional.of(receiver));
        when(encoder.matches("1234", sender.getTransactionPinHash())).thenReturn(true);
        when(walletRepo.findByUser(sender)).thenReturn(Optional.of(senderWallet));
        when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> walletService.sendMoney(1L, req));
        assertEquals("Insufficient balance!", ex.getMessage());
    }

    @Test
    void testSendMoney_ExceedingDailyLimit_ThrowsException() {
        TransactionRequest req = new TransactionRequest("receiver@revpay.com", new BigDecimal("50001.00"), "OverLimit", "1234");

        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepo.findByEmail("receiver@revpay.com")).thenReturn(Optional.of(receiver));
        when(encoder.matches("1234", sender.getTransactionPinHash())).thenReturn(true);
        when(walletRepo.findByUser(sender)).thenReturn(Optional.of(senderWallet));
        when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> walletService.sendMoney(1L, req));
        assertTrue(ex.getMessage().contains("limit of ₹50,000 exceeded"));
    }

    @Test
    void testAddFunds_Success() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(walletRepo.findByUser(sender)).thenReturn(Optional.of(senderWallet));
        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.addFunds(1L, new BigDecimal("500.00"), "Credit Card");

        assertEquals(new BigDecimal("5500.00"), senderWallet.getBalance());
        assertNotNull(result);
        verify(notificationService).createNotification(eq(1L), anyString(), eq("WALLET"));
    }

    @Test
    void testWithdrawFunds_Success() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(walletRepo.findByUser(sender)).thenReturn(Optional.of(senderWallet));
        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.withdrawFunds(1L, new BigDecimal("300.00"));

        assertEquals(new BigDecimal("4700.00"), senderWallet.getBalance());
        assertNotNull(result);
        verify(notificationService).createNotification(eq(1L), anyString(), eq("WALLET"));
    }

    @Test
    void testWithdrawFunds_WithInsufficientBalance_ThrowsException() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(walletRepo.findByUser(sender)).thenReturn(Optional.of(senderWallet));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> walletService.withdrawFunds(1L, new BigDecimal("6000.00")));
        assertEquals("Insufficient balance", ex.getMessage());
    }

    @Test
    void testGetBalance_ReturnsCorrectAmount() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
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
        pendingRequest.setSender(receiver);
        pendingRequest.setReceiver(sender);
        pendingRequest.setAmount(new BigDecimal("200.00"));
        pendingRequest.setType(Transaction.TransactionType.REQUEST);
        pendingRequest.setStatus(Transaction.TransactionStatus.PENDING);

        when(transRepo.findById(100L)).thenReturn(Optional.of(pendingRequest));
        when(userRepo.findById(receiver.getUserId())).thenReturn(Optional.of(receiver));
        when(userRepo.findByEmail(sender.getEmail())).thenReturn(Optional.of(sender));
        when(encoder.matches("1234", receiver.getTransactionPinHash())).thenReturn(true);
        when(walletRepo.findByUser(receiver)).thenReturn(Optional.of(receiverWallet));
        when(walletRepo.findByUser(sender)).thenReturn(Optional.of(senderWallet));
        when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());
        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

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

        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(paymentMethodRepo.findByUser(sender)).thenReturn(new ArrayList<>());
        when(paymentMethodRepo.save(any(PaymentMethod.class))).thenReturn(newCard);

        PaymentMethod result = walletService.addCard(1L, newCard);

        assertNotNull(result);
        verify(notificationService).createNotification(eq(1L), anyString(), eq("SECURITY"));
    }

    @Test
    void testDeleteCard_RemovesCard() {
        when(paymentMethodRepo.findById(1L)).thenReturn(Optional.of(card));

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

        when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(sender, sender))
                .thenReturn(Collections.singletonList(tx));  // Fixed: using Collections.singletonList()

        WalletAnalyticsDTO result = walletService.getSpendingAnalytics(sender);

        assertNotNull(result);
        assertEquals(new BigDecimal("200.00"), result.getTotalSpent());
        assertEquals(1, result.getTransactionCount());
    }
}