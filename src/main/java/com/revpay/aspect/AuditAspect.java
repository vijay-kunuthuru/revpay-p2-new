package com.revpay.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @AfterReturning(pointcut = "execution(* com.revpay.service.WalletService.sendMoney(..)) || execution(* com.revpay.service.WalletService.addFunds(..)) || execution(* com.revpay.service.WalletService.withdrawFunds(..)) || execution(* com.revpay.service.LoanService.approveLoan(..))")
    public void logAuditActivity(JoinPoint joinPoint) {
        log.info("AUDIT: Executed {} on {}", joinPoint.getSignature().getName(), joinPoint.getSignature().getDeclaringTypeName());
    }
}