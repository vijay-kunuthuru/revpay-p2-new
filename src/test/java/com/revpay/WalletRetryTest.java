package com.revpay;

import com.revpay.model.dto.TransactionRequest;
import com.revpay.model.entity.User;
import com.revpay.repository.TransactionRepository;
import com.revpay.repository.UserRepository;
import com.revpay.repository.WalletRepository;
import com.revpay.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
public class WalletRetryTest {

    @Autowired
    private WalletService walletService;

    @MockitoBean
    private WalletRepository walletRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Test
    void testDeadlockRetryExhaustion() {
        User sender = new User();
        sender.setUserId(1L);
        sender.setTransactionPinHash("hash");

        User receiver = new User();
        receiver.setUserId(2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(receiver));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        when(walletRepository.findByUserUserIdForUpdate(any()))
                .thenThrow(new DeadlockLoserDataAccessException("Simulated Deadlock", null));

        TransactionRequest request = new TransactionRequest("receiver@revpay.com", new BigDecimal("100"), "test", "pin");

        assertThrows(DeadlockLoserDataAccessException.class, () -> walletService.sendMoney(1L, request));

        verify(walletRepository, times(3)).findByUserUserIdForUpdate(any());
    }
}