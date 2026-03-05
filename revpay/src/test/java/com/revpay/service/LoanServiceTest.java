package com.revpay.service;

import com.revpay.model.dto.*;
import com.revpay.model.entity.*;
import com.revpay.repository.LoanInstallmentRepository;
import com.revpay.repository.LoanRepository;
import com.revpay.repository.UserRepository;
import com.revpay.util.NotificationUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LoanServiceTest {

        @Mock
        private LoanRepository loanRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private WalletService walletService;
        @Mock
        private LoanInstallmentRepository installmentRepository;
        @Mock
        private NotificationService notificationService;

        @InjectMocks
        private LoanService loanService;

        private User businessUser;

        @Before
        public void setup() {
                businessUser = new User();
                businessUser.setUserId(1L);
                businessUser.setRole(Role.BUSINESS);
        }

        // ───────────────── APPLY ─────────────────

        @Test
        public void testApplyLoanSuccess() {
                LoanApplyDTO dto = new LoanApplyDTO();
                dto.setAmount(BigDecimal.valueOf(50000));
                dto.setTenureMonths(12);
                dto.setPurpose("Business Expansion");

                when(userRepository.findById(1L)).thenReturn(Optional.of(businessUser));
                when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.PAID))
                                .thenReturn(Collections.emptyList());
                when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.OVERDUE))
                                .thenReturn(Collections.emptyList());

                LoanResponseDTO response = loanService.applyLoan(1L, dto);

                assertEquals(BigDecimal.valueOf(50000), response.getAmount());
                assertEquals(LoanStatus.APPLIED, response.getStatus());

                verify(loanRepository).save(any(Loan.class));
                verify(notificationService)
                                .createNotification(eq(1L), anyString(), eq("LOAN"));
        }

        @Test(expected = RuntimeException.class)
        public void testApplyLoanFailsForNonBusinessUser() {
                User personal = new User();
                personal.setUserId(2L);
                personal.setRole(Role.PERSONAL);

                when(userRepository.findById(2L)).thenReturn(Optional.of(personal));

                LoanApplyDTO dto = new LoanApplyDTO();
                dto.setAmount(BigDecimal.valueOf(10000));
                dto.setTenureMonths(6);

                loanService.applyLoan(2L, dto);
        }

        // ───────────────── APPROVE ─────────────────

        @Test
        public void testApproveLoanSuccess() {

                Loan loan = Loan.builder()
                                .loanId(1L)
                                .user(businessUser)
                                .amount(BigDecimal.valueOf(100000))
                                .tenureMonths(12)
                                .status(LoanStatus.APPLIED)
                                .build();

                when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

                LoanApprovalDTO dto = LoanApprovalDTO.builder()
                                .loanId(1L)
                                .approved(true)
                                .interestRate(BigDecimal.valueOf(10))
                                .build();

                loanService.approveLoan(1L, dto);

                verify(walletService).disburseLoanFromAdmin(
                                anyLong(),
                                eq(1L),
                                eq(BigDecimal.valueOf(100000)),
                                contains("Loan Disbursement"));

                verify(notificationService)
                                .createNotification(eq(1L), anyString(), eq("LOAN"));

                assertEquals(LoanStatus.ACTIVE, loan.getStatus());
        }

        @Test(expected = RuntimeException.class)
        public void testApproveLoanFailsIfAlreadyActive() {

                Loan loan = Loan.builder()
                                .loanId(1L)
                                .user(businessUser)
                                .status(LoanStatus.ACTIVE)
                                .build();

                when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

                LoanApprovalDTO dto = LoanApprovalDTO.builder()
                                .loanId(1L)
                                .approved(true)
                                .build();

                loanService.approveLoan(1L, dto);
        }

        // ───────────────── REPAY ─────────────────

        @Test
        public void testRepayLoanPendingEmi() {

                Loan loan = Loan.builder()
                                .loanId(1L)
                                .user(businessUser)
                                .remainingAmount(BigDecimal.valueOf(10000))
                                .status(LoanStatus.ACTIVE)
                                .build();

                LoanInstallment emi = LoanInstallment.builder()
                                .loan(loan)
                                .amount(BigDecimal.valueOf(1000))
                                .status(InstallmentStatus.PENDING)
                                .dueDate(LocalDate.now().minusDays(1))
                                .build();

                when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
                when(installmentRepository.findByLoan_LoanId(1L))
                                .thenReturn(List.of(emi));

                LoanRepayDTO dto = new LoanRepayDTO();
                dto.setLoanId(1L);

                loanService.repayLoan(1L, dto);

                verify(walletService).withdrawFundsForLoan(
                                eq(1L),
                                eq(BigDecimal.valueOf(1000)),
                                contains("Loan Repayment"));

                assertEquals(InstallmentStatus.PAID, emi.getStatus());
                assertEquals(0,
                                BigDecimal.valueOf(9000).compareTo(loan.getRemainingAmount()));
        }

        @Test
        public void testRepayLoanOverdueEmi() {

                Loan loan = Loan.builder()
                                .loanId(1L)
                                .user(businessUser)
                                .remainingAmount(BigDecimal.valueOf(5000))
                                .status(LoanStatus.ACTIVE)
                                .build();

                LoanInstallment emi = LoanInstallment.builder()
                                .loan(loan)
                                .amount(BigDecimal.valueOf(1000))
                                .status(InstallmentStatus.OVERDUE)
                                .dueDate(LocalDate.now().minusDays(2))
                                .build();

                when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
                when(installmentRepository.findByLoan_LoanId(1L))
                                .thenReturn(List.of(emi));

                LoanRepayDTO dto = new LoanRepayDTO();
                dto.setLoanId(1L);

                loanService.repayLoan(1L, dto);

                verify(walletService).withdrawFundsForLoan(
                                eq(1L),
                                eq(BigDecimal.valueOf(1100)),
                                contains("Loan Repayment"));

                assertEquals(0,
                                BigDecimal.valueOf(3900).compareTo(loan.getRemainingAmount()));
        }

        @Test(expected = RuntimeException.class)
        public void testRepayLoanFailsIfClosed() {

                Loan loan = Loan.builder()
                                .loanId(1L)
                                .user(businessUser)
                                .status(LoanStatus.CLOSED)
                                .build();

                when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

                LoanRepayDTO dto = new LoanRepayDTO();
                dto.setLoanId(1L);

                loanService.repayLoan(1L, dto);
        }

        // ───────────────── PRECLOSE ─────────────────

        @Test
        public void testPreCloseLoan() {

                Loan loan = Loan.builder()
                                .loanId(1L)
                                .user(businessUser)
                                .remainingAmount(BigDecimal.valueOf(10000))
                                .status(LoanStatus.ACTIVE)
                                .build();

                when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

                loanService.preCloseLoan(1L, 1L);

                verify(walletService).withdrawFundsForLoan(
                                eq(1L),
                                eq(new BigDecimal("10200.00")),
                                contains("Loan Pre-Closure"));

                assertEquals(LoanStatus.CLOSED, loan.getStatus());
                assertEquals(0, BigDecimal.ZERO.compareTo(loan.getRemainingAmount()));
        }

        // ───────────────── CREDIT SCORE ─────────────────

    @Test
    public void testCreditScoreCalculation() {

        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.PAID))
                .thenReturn(List.of(new LoanInstallment()));

        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.OVERDUE))
                .thenReturn(List.of(new LoanInstallment()));

        int score = loanService.calculateCreditScore(1L);

        assertEquals(697, score);
    }

        // ───────────────── OVERDUE ─────────────────

        @Test
        public void testMarkOverdueInstallments() {

                Loan loan = Loan.builder().user(businessUser).build();

                LoanInstallment past = LoanInstallment.builder()
                                .loan(loan)
                                .status(InstallmentStatus.PENDING)
                                .dueDate(LocalDate.now().minusDays(1))
                                .build();

                LoanInstallment future = LoanInstallment.builder()
                                .loan(loan)
                                .status(InstallmentStatus.PENDING)
                                .dueDate(LocalDate.now().plusDays(5))
                                .build();

                when(installmentRepository.findAllByStatus(InstallmentStatus.PENDING))
                                .thenReturn(List.of(past, future));

                loanService.markOverdueInstallments();

                assertEquals(InstallmentStatus.OVERDUE, past.getStatus());
                assertEquals(InstallmentStatus.PENDING, future.getStatus());

                verify(installmentRepository).saveAll(List.of(past));
        }
}