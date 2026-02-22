package com.revpay.service;

import com.revpay.model.dto.TransactionRequest;
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
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WalletService walletService;

    private User sender;
    private Wallet senderWallet;

    @BeforeEach
    void setup() {
        sender = new User();
        sender.setUserId(1L);
        sender.setEmail("sender@revpay.com");
        sender.setTransactionPinHash("hashed_1234");
        sender.setFullName("Sender Name");

        senderWallet = new Wallet();
        senderWallet.setBalance(new BigDecimal("1000.00"));
        senderWallet.setUser(sender);
    }

    @Test
    void testSendMoney_Success() {
        TransactionRequest req = new TransactionRequest("receiver@mail.com", new BigDecimal("200.00"), "Lunch", "1234");
        User receiver = new User();
        receiver.setUserId(2L);
        receiver.setEmail("receiver@mail.com");
        receiver.setFullName("Receiver Name");

        Wallet receiverWallet = new Wallet();
        receiverWallet.setBalance(new BigDecimal("50.00"));
        receiverWallet.setUser(receiver);

        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepo.findByEmail("receiver@mail.com")).thenReturn(Optional.of(receiver));
        when(encoder.matches("1234", sender.getTransactionPinHash())).thenReturn(true);

        lenient().when(walletRepo.findById(1L)).thenReturn(Optional.of(senderWallet));
        lenient().when(walletRepo.findById(2L)).thenReturn(Optional.of(receiverWallet));
        lenient().when(walletRepo.findByUserUserIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        lenient().when(walletRepo.findByUserUserIdForUpdate(2L)).thenReturn(Optional.of(receiverWallet));

        when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        when(transRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.sendMoney(1L, req);

        assertEquals(new BigDecimal("800.00"), senderWallet.getBalance());
        assertEquals(new BigDecimal("250.00"), receiverWallet.getBalance());
        assertNotNull(result);
        verify(walletRepo, times(2)).save(any(Wallet.class));
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void testSendMoney_WrongPin_ShouldFail() {
        TransactionRequest req = new TransactionRequest("r@mail.com", new BigDecimal("100.00"), "test", "wrong_pin");

        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepo.findByEmail("r@mail.com")).thenReturn(Optional.of(new User()));
        when(encoder.matches("wrong_pin", sender.getTransactionPinHash())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> walletService.sendMoney(1L, req));
        assertEquals("Invalid Transaction PIN!", ex.getMessage());
    }

    @Test
    void testSendMoney_LowBalance_ShouldFail() {
        TransactionRequest req = new TransactionRequest("r@mail.com", new BigDecimal("5000.00"), "broke", "1234");
        User receiver = new User();
        receiver.setUserId(2L);

        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepo.findByEmail("r@mail.com")).thenReturn(Optional.of(receiver));
        when(encoder.matches("1234", sender.getTransactionPinHash())).thenReturn(true);

        lenient().when(walletRepo.findById(1L)).thenReturn(Optional.of(senderWallet));
        lenient().when(walletRepo.findById(2L)).thenReturn(Optional.of(new Wallet()));
        lenient().when(walletRepo.findByUserUserIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        lenient().when(walletRepo.findByUserUserIdForUpdate(2L)).thenReturn(Optional.of(new Wallet()));

        when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> walletService.sendMoney(1L, req));
        assertEquals("Insufficient balance!", ex.getMessage());
    }

    @Test
    void testDailyLimit_Exceeded_ShouldFail() {
        TransactionRequest req = new TransactionRequest("r@mail.com", new BigDecimal("50001.00"), "OverLimit", "1234");

        when(userRepo.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepo.findByEmail("r@mail.com")).thenReturn(Optional.of(new User()));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        when(transRepo.findBySenderOrReceiverOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> walletService.sendMoney(1L, req));
        assertTrue(ex.getMessage().contains("limit of INR50,000 exceeded"));
    }

    @Test
    void testTransactionReference_Format() {
        lenient().when(walletRepo.findById(1L)).thenReturn(Optional.of(senderWallet));
        lenient().when(walletRepo.findByUserUserIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(transRepo.save(any())).thenAnswer(invocation -> invocation.getArguments()[0]);

        Transaction result = walletService.addFunds(1L, new BigDecimal("100.00"), "Bonus");

        assertNotNull(result.getTransactionRef());
        assertTrue(result.getTransactionRef().startsWith("TXN-"));
    }
}