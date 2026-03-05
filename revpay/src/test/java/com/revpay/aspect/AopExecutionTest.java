package com.revpay.aspect;

import com.revpay.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class AopExecutionTest {

    @Autowired
    private WalletService walletService;

    @Test
    void testServiceIsProxied() {
        assertTrue(AopUtils.isAopProxy(walletService));
    }
}