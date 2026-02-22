package com.revpay.logging;

import com.revpay.exception.GlobalExceptionHandler;
import com.revpay.model.dto.TransactionRequest;
import com.revpay.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
public class LogLeakageTest {

    @Autowired
    private WalletService walletService;

    @Test
    void testExceptionLoggingDoesNotLeakPin(CapturedOutput output) {
        TransactionRequest request = new TransactionRequest("receiver@revpay.com", new BigDecimal("100"), "desc", "SECRET_PIN_9999");
        
        assertThrows(RuntimeException.class, () -> walletService.sendMoney(999L, request));

        assertFalse(output.getOut().contains("SECRET_PIN_9999"));
    }
}