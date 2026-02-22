package com.revpay;

import com.revpay.model.dto.TransactionRequest;
import com.revpay.model.entity.Transaction;
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

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class WalletServiceBranchTest {

    @Autowired private WalletService walletService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long senderId;

    @BeforeEach
    void setup() {
        transactionTemplate.execute(status -> {
            transactionRepository.deleteAllInBatch();
            walletRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            String pw = passwordEncoder.encode("password");
            String pin = passwordEncoder.encode("1234");

            User s = new User();
            s.setEmail("limit_tester@revpay.com");
            s.setPhoneNumber("8888888888");
            s.setPasswordHash(pw);
            s.setTransactionPinHash(pin);
            s.setFullName("Limit Tester");
            s.setRole(Role.PERSONAL);
            s.setActive(true);
            s = userRepository.save(s);
            senderId = s.getUserId();

            Wallet sw = new Wallet();
            sw.setUser(s);
            sw.setBalance(new BigDecimal("100000.00"));
            sw.setCurrency("INR");
            walletRepository.save(sw);

            User r = new User();
            r.setEmail("limit_receiver@revpay.com");
            r.setPhoneNumber("8888888889");
            r.setPasswordHash(pw);
            r.setTransactionPinHash(pin);
            r.setFullName("Limit Receiver");
            r.setRole(Role.PERSONAL);
            r.setActive(true);
            r = userRepository.save(r);

            Wallet rw = new Wallet();
            rw.setUser(r);
            rw.setBalance(new BigDecimal("0.00"));
            rw.setCurrency("INR");
            walletRepository.save(rw);

            Transaction tx1 = new Transaction();
            tx1.setSender(s);
            tx1.setReceiver(r);
            tx1.setAmount(new BigDecimal("50000.00"));
            tx1.setType(Transaction.TransactionType.SEND);
            tx1.setStatus(Transaction.TransactionStatus.COMPLETED);
            transactionRepository.save(tx1);

            return null;
        });
    }

    @Test
    void testDailyLimitExactExhaustion() {
        TransactionRequest request = new TransactionRequest(
                "limit_receiver@revpay.com",
                new BigDecimal("0.01"),
                "Over Limit",
                "1234"
        );
        assertThrows(RuntimeException.class, () -> walletService.sendMoney(senderId, request));
    }
}