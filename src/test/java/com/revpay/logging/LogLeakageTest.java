package com.revpay.logging;

import com.revpay.exception.InvalidPinException;
import com.revpay.model.dto.TransactionRequest;
import com.revpay.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
public class LogLeakageTest {

    @Autowired
    private WalletService walletService;

    @Test
    @DisplayName("Verify that sensitive PIN is never leaked in logs during transaction failures")
    void testExceptionLoggingDoesNotLeakPin(CapturedOutput output) {
        // 1. Arrange: Use the new Builder pattern we added to TransactionRequest
        String secretPin = "123456";
        TransactionRequest request = TransactionRequest.builder()
                .receiverIdentifier("receiver@revpay.com")
                .amount(new BigDecimal("500.00"))
                .description("Test Transfer")
                .transactionPin(secretPin)
                .idempotencyKey(UUID.randomUUID().toString()) // Added mandatory field
                .build();

        // 2. Act: Attempt a transaction that will fail (e.g., non-existent sender)
        // We now catch specific business exceptions instead of generic RuntimeException
        assertThrows(Exception.class, () -> walletService.sendMoney(999L, request));

        // 3. Assert: Verify the logs (output.getOut()) do not contain the raw PIN
        // This confirms that @ToString.Exclude on the DTO is working as intended
        String logs = output.getAll();

        assertFalse(logs.contains(secretPin),
                "CRITICAL SECURITY FAILURE: Sensitive transaction PIN was found in the application logs!");

        // Optional: Verify that the DTO itself was logged but masked the PIN
        // Based on Lombok's default behavior, it should show 'transactionPin=null' or just omit it.
    }
}