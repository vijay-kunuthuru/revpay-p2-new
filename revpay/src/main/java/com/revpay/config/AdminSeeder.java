package com.revpay.config;

import com.revpay.model.entity.Role;
import com.revpay.model.entity.User;
import com.revpay.model.entity.Wallet;
import com.revpay.repository.UserRepository;
import com.revpay.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {

        User admin = userRepository.findByEmail("admin@revpay.com").orElse(null);

        if (admin == null) {
            log.info("Seeding default Admin user into the database...");
            admin = new User();
            admin.setFullName("System Admin");
            admin.setEmail("admin@revpay.com");
            admin.setPhoneNumber("0000000000");
        } else {
            log.info("Admin user exists. Forcefully resetting role and password to admin@123...");
        }

        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode("admin@123"));
        admin.setTransactionPinHash(passwordEncoder.encode("0000"));
        admin.setSecurityQuestion("Admin Code?");
        admin.setSecurityAnswerHash(passwordEncoder.encode("Alpha"));
        admin.setActive(true); // make sure it's active!

        admin = userRepository.save(admin);

        Wallet adminWallet = walletRepository.findByUser(admin).orElse(null);
        if (adminWallet == null) {
            log.info("Creating default Wallet for Admin user...");
            adminWallet = new Wallet();
            adminWallet.setUser(admin);
            adminWallet.setBalance(BigDecimal.ZERO);
            adminWallet.setCurrency("INR");
            walletRepository.save(adminWallet);
        }

        log.info("Default Admin User seeded/updated successfully.");
    }
}