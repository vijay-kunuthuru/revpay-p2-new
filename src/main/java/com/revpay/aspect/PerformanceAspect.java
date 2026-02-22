package com.revpay.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PerformanceAspect {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Around("execution(* com.revpay.service..*(..))")
    public Object profileServiceMethods(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object output = pjp.proceed();
        long elapsedTime = System.currentTimeMillis() - start;
        log.info("Execution time: {}.{}() - {} ms",
                pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName(), elapsedTime);
        return output;
    }
}