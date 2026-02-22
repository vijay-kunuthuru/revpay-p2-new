package com.revpay;

import com.revpay.model.dto.TransactionRequest;
import com.revpay.model.entity.User;
import com.revpay.model.entity.Wallet;
import com.revpay.model.entity.Role;
import com.revpay.repository.TransactionRepository;
import com.revpay.repository.UserRepository;
import com.revpay.repository.WalletRepository;
import com.revpay.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class FinancialIntegrityStressTest {

    @Autowired private WalletService walletService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long senderId;
    private String receiverEmail = "stress_receiver@revpay.com";

    @BeforeEach
    void setup() {
        // Run setup in a guaranteed, committed transaction
        transactionTemplate.execute(status -> {
            transactionRepository.deleteAllInBatch();
            walletRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            String pw = passwordEncoder.encode("password");
            String pin = passwordEncoder.encode("1234");

            User s = new User();
            s.setEmail("stress_sender@revpay.com");
            s.setPhoneNumber("9999999991");
            s.setPasswordHash(pw);
            s.setTransactionPinHash(pin);
            s.setFullName("Stress Sender");
            s.setRole(Role.PERSONAL);
            s.setActive(true);
            s = userRepository.save(s);
            senderId = s.getUserId();

            Wallet sw = new Wallet();
            sw.setUser(s);
            sw.setBalance(new BigDecimal("10000.00"));
            sw.setCurrency("INR");
            walletRepository.save(sw);

            User r = new User();
            r.setEmail(receiverEmail);
            r.setPhoneNumber("9999999992");
            r.setPasswordHash(pw);
            r.setTransactionPinHash(pin);
            r.setFullName("Stress Receiver");
            r.setRole(Role.PERSONAL);
            r.setActive(true);
            r = userRepository.save(r);

            Wallet rw = new Wallet();
            rw.setUser(r);
            rw.setBalance(new BigDecimal("0.00"));
            rw.setCurrency("INR");
            walletRepository.save(rw);

            return null;
        });
    }

    @Test
    void test100ConcurrentTransfers() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        TransactionRequest request = new TransactionRequest(
                receiverEmail,
                new BigDecimal("10.00"),
                "Stress Transfer",
                "1234"
        );

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    walletService.sendMoney(senderId, request);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();

        Wallet finalSenderWallet = walletRepository.findByUserUserId(senderId).orElseThrow();
        BigDecimal expectedDeduction = new BigDecimal("10.00").multiply(new BigDecimal(successCount.get()));
        BigDecimal expectedSenderBalance = new BigDecimal("10000.00").subtract(expectedDeduction);

        assertEquals(0, expectedSenderBalance.compareTo(finalSenderWallet.getBalance()));
    }
}