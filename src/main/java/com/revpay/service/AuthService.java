package com.revpay.service;

import com.revpay.exception.UserAlreadyExistsException;
import com.revpay.model.dto.ForgotPasswordRequest;
import com.revpay.model.dto.ResetPasswordRequest;
import com.revpay.model.dto.SignupRequest;
import com.revpay.model.dto.UpdatePasswordRequest;
import com.revpay.model.entity.BusinessProfile;
import com.revpay.model.entity.Role;
import com.revpay.model.entity.User;
import com.revpay.model.entity.Wallet;
import com.revpay.repository.BusinessProfileRepository;
import com.revpay.repository.UserRepository;
import com.revpay.repository.WalletRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private BusinessProfileRepository businessRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Transactional
    public void registerUser(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            logger.warn("REGISTRATION_FAILED | Email already exists: {}", request.getEmail());
            throw new UserAlreadyExistsException("Error: Email is already in use!");
        }

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            logger.warn("REGISTRATION_FAILED | Phone number already exists");
            throw new UserAlreadyExistsException("Error: Phone number is already in use!");
        }

        logger.info("REGISTRATION_START | Email: {}", request.getEmail());

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setTransactionPinHash(passwordEncoder.encode(request.getTransactionPin()));
        user.setSecurityQuestion(request.getSecurityQuestion());
        user.setSecurityAnswerHash(passwordEncoder.encode(request.getSecurityAnswer()));

        String strRole = request.getRole();
        if (strRole == null) {
            user.setRole(Role.PERSONAL);
        } else {
            switch (strRole.toLowerCase()) {
                case "admin":
                    user.setRole(Role.ADMIN);
                    break;
                case "business":
                    user.setRole(Role.BUSINESS);
                    break;
                default:
                    user.setRole(Role.PERSONAL);
            }
        }

        User savedUser = userRepository.save(user);

        Wallet wallet = new Wallet();
        wallet.setUser(savedUser);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setCurrency("INR");
        walletRepository.save(wallet);

        if (savedUser.getRole() == Role.BUSINESS) {
            BusinessProfile profile = new BusinessProfile();
            profile.setUser(savedUser);
            profile.setBusinessName(request.getFullName() + " Business");
            businessRepository.save(profile);
        }

        logger.info("REGISTRATION_SUCCESS | UserID: {} | Wallet Created", savedUser.getUserId());
    }

    public String getSecurityQuestion(String email) {
        logger.info("SECURITY_QUESTION_REQUEST | Email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found for the provided email."));
        return user.getSecurityQuestion();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        logger.info("PASSWORD_RESET_ATTEMPT | Email: {}", request.getEmail());
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid request details."));

        if (!passwordEncoder.matches(request.getSecurityAnswer(), user.getSecurityAnswerHash())) {
            logger.warn("PASSWORD_RESET_FAILED | Security answer mismatch | Email: {}", request.getEmail());
            throw new RuntimeException("Security answer is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        logger.info("PASSWORD_RESET_SUCCESS | Email: {}", request.getEmail());
    }

    @Transactional
    public void updatePassword(Long userId, UpdatePasswordRequest request) {
        logger.info("PASSWORD_UPDATE_ATTEMPT | UserID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            logger.warn("PASSWORD_UPDATE_FAILED | Old password mismatch | UserID: {}", userId);
            throw new RuntimeException("Current password does not match.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        logger.info("PASSWORD_UPDATE_SUCCESS | UserID: {}", userId);
    }
}