package com.revpay;

import com.revpay.model.dto.SignupRequest;
import com.revpay.model.entity.User;
import com.revpay.model.entity.Wallet;
import com.revpay.repository.UserRepository;
import com.revpay.repository.WalletRepository;
import com.revpay.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
public class RegistrationIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void testCompleteRegistrationFlow() {
        long timestamp = System.currentTimeMillis();
        String uniqueEmail = "test" + timestamp + "@revpay.com";
        String uniquePhone = String.valueOf(timestamp).substring(0, 10);

        SignupRequest req = new SignupRequest();
        req.setEmail(uniqueEmail);
        req.setPassword("pass123");
        req.setFullName("Test User");
        req.setPhoneNumber(uniquePhone);
        req.setRole("PERSONAL");
        req.setTransactionPin("1122");
        req.setSecurityQuestion("Your City?");
        req.setSecurityAnswer("Salem");

        authService.registerUser(req);

        Optional<User> savedUser = userRepository.findByEmail(uniqueEmail);
        assertTrue(savedUser.isPresent());

        Optional<Wallet> savedWallet = walletRepository.findById(savedUser.get().getUserId());
        assertTrue(savedWallet.isPresent());

        assertEquals(0, savedWallet.get().getBalance().compareTo(java.math.BigDecimal.ZERO));
    }
}