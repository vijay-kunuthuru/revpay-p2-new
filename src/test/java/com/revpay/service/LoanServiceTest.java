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

    @Mock private LoanRepository loanRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private LoanInstallmentRepository installmentRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private LoanService loanService;

    private User businessUser;

    @Before
    public void setup() {
        businessUser = new User();
        businessUser.setUserId(1L);
        businessUser.setRole(Role.BUSINESS);
    }

    // ── APPLY ────────────────────────────────────────────────────────────────

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
        verify(loanRepository, times(1)).save(any(Loan.class));
        verify(notificationService).createNotification(
                eq(1L), eq(NotificationUtil.loanApplied(dto.getAmount())), eq("LOAN"));
    }

    @Test(expected = RuntimeException.class)
    public void testApplyLoanFailsForNonBusinessUser() {
        User personalUser = new User();
        personalUser.setUserId(2L);
        personalUser.setRole(Role.PERSONAL);

        when(userRepository.findById(2L)).thenReturn(Optional.of(personalUser));

        LoanApplyDTO dto = new LoanApplyDTO();
        dto.setAmount(BigDecimal.valueOf(10000));
        dto.setTenureMonths(6);
        dto.setPurpose("Test");

        loanService.applyLoan(2L, dto);
    }

    @Test
    public void testApplyLoanNotSavedIfIneligible() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(businessUser));

        List<LoanInstallment> overdues = Collections.nCopies(8,
                LoanInstallment.builder()
                        .loan(Loan.builder().user(businessUser).build())
                        .status(InstallmentStatus.OVERDUE).build());

        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.PAID))
                .thenReturn(Collections.emptyList());
        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.OVERDUE))
                .thenReturn(overdues);

        LoanApplyDTO dto = new LoanApplyDTO();
        dto.setAmount(BigDecimal.valueOf(300000));
        dto.setTenureMonths(12);
        dto.setPurpose("Equipment");

        try {
            loanService.applyLoan(1L, dto);
            fail("Expected RuntimeException for ineligible user");
        } catch (RuntimeException e) {
            verify(loanRepository, never()).save(any(Loan.class));
        }
    }


    @Test
    public void testApproveLoan() {
        Loan loan = Loan.builder()
                .loanId(1L).user(businessUser)
                .amount(BigDecimal.valueOf(100000))
                .tenureMonths(12).status(LoanStatus.APPLIED).build();

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        LoanApprovalDTO dto = LoanApprovalDTO.builder()
                .loanId(1L).approved(true)
                .interestRate(BigDecimal.valueOf(10)).build();

        loanService.approveLoan(dto);

        verify(walletService).addFunds(eq(1L), any(), eq("Loan Disbursement"));
        verify(notificationService).createNotification(
                eq(1L), eq(NotificationUtil.loanApproved()), eq("LOAN"));
        assertEquals(LoanStatus.ACTIVE, loan.getStatus());
    }

    @Test
    public void testRejectLoan() {
        Loan loan = Loan.builder()
                .loanId(2L).user(businessUser)
                .amount(BigDecimal.valueOf(50000))
                .tenureMonths(6).status(LoanStatus.APPLIED).build();

        when(loanRepository.findById(2L)).thenReturn(Optional.of(loan));

        LoanApprovalDTO dto = LoanApprovalDTO.builder()
                .loanId(2L).approved(false).build();

        loanService.approveLoan(dto);

        assertEquals(LoanStatus.REJECTED, loan.getStatus());
        verify(walletService, never()).addFunds(any(), any(), any());
        verify(notificationService).createNotification(
                eq(1L), eq(NotificationUtil.loanRejected()), eq("LOAN"));
    }


    @Test
    public void testRepayLoan_PendingEmi() {
        Loan loan = Loan.builder()
                .loanId(1L).user(businessUser)
                .remainingAmount(BigDecimal.valueOf(10000))
                .status(LoanStatus.ACTIVE).build();

        LoanInstallment emi = LoanInstallment.builder()
                .loan(loan).amount(BigDecimal.valueOf(1000))
                .status(InstallmentStatus.PENDING).installmentNumber(1).build();

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(installmentRepository.findByLoan_LoanId(1L)).thenReturn(List.of(emi));

        LoanRepayDTO dto = new LoanRepayDTO();
        dto.setLoanId(1L);

        String result = loanService.repayLoan(1L, dto);

        assertEquals("EMI Paid Successfully", result);
        verify(walletService).withdrawFunds(1L, BigDecimal.valueOf(1000));
        assertEquals(InstallmentStatus.PAID, emi.getStatus());
        assertEquals(0, BigDecimal.valueOf(9000).compareTo(loan.getRemainingAmount()));
    }

    @Test
    public void testRepayLoan_OverdueEmiWithPenalty() {
        Loan loan = Loan.builder()
                .loanId(1L).user(businessUser)
                .remainingAmount(BigDecimal.valueOf(5000))
                .status(LoanStatus.ACTIVE).build();

        LoanInstallment overdueEmi = LoanInstallment.builder()
                .loan(loan).amount(BigDecimal.valueOf(1000))
                .status(InstallmentStatus.OVERDUE).installmentNumber(1).build();

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(installmentRepository.findByLoan_LoanId(1L)).thenReturn(List.of(overdueEmi));

        LoanRepayDTO dto = new LoanRepayDTO();
        dto.setLoanId(1L);

        loanService.repayLoan(1L, dto);

        // Wallet: EMI ₹1000 + penalty ₹100 = ₹1100
        verify(walletService).withdrawFunds(1L, BigDecimal.valueOf(1100));
        // Remaining balance must also drop by ₹1100
        assertEquals(0, BigDecimal.valueOf(3900).compareTo(loan.getRemainingAmount()));
    }

    @Test
    public void testRepayLoan_ClosesLoanWhenFullyPaid() {
        Loan loan = Loan.builder()
                .loanId(1L).user(businessUser)
                .remainingAmount(BigDecimal.valueOf(1000))
                .status(LoanStatus.ACTIVE).build();

        LoanInstallment lastEmi = LoanInstallment.builder()
                .loan(loan).amount(BigDecimal.valueOf(1000))
                .status(InstallmentStatus.PENDING).installmentNumber(12).build();

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(installmentRepository.findByLoan_LoanId(1L)).thenReturn(List.of(lastEmi));

        LoanRepayDTO dto = new LoanRepayDTO();
        dto.setLoanId(1L);

        loanService.repayLoan(1L, dto);

        assertEquals(LoanStatus.CLOSED, loan.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(loan.getRemainingAmount()));
    }


    @Test
    public void testCreditScoreCalculation() {
        Loan loan = Loan.builder().user(businessUser).build();

        LoanInstallment paid = LoanInstallment.builder()
                .loan(loan).status(InstallmentStatus.PAID).build();
        LoanInstallment overdue = LoanInstallment.builder()
                .loan(loan).status(InstallmentStatus.OVERDUE).build();

        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.PAID))
                .thenReturn(List.of(paid));
        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.OVERDUE))
                .thenReturn(List.of(overdue));

        int score = loanService.calculateCreditScore(1L);

        assertEquals(697, score); // 700 + (1*2) - (1*5) = 697
        assertTrue(score >= 300 && score <= 850);
    }

    @Test
    public void testCreditScoreCappedAt850() {
        List<LoanInstallment> manyPaid = Collections.nCopies(100,
                LoanInstallment.builder()
                        .loan(Loan.builder().user(businessUser).build())
                        .status(InstallmentStatus.PAID).build());

        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.PAID))
                .thenReturn(manyPaid);
        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.OVERDUE))
                .thenReturn(Collections.emptyList());

        assertEquals(850, loanService.calculateCreditScore(1L));
    }

    @Test
    public void testLoanEligibility_NewUserIsEligible() {
        when(installmentRepository.findByUserIdAndStatus(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        assertTrue(loanService.isEligibleForLoan(1L, BigDecimal.valueOf(10000)));
    }

    @Test
    public void testLoanEligibility_HighRiskNotEligible() {
        List<LoanInstallment> overdues = Collections.nCopies(20,
                LoanInstallment.builder()
                        .loan(Loan.builder().user(businessUser).build())
                        .status(InstallmentStatus.OVERDUE).build());

        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.PAID))
                .thenReturn(Collections.emptyList());
        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.OVERDUE))
                .thenReturn(overdues);

        assertFalse(loanService.isEligibleForLoan(1L, BigDecimal.valueOf(10000)));
    }


    @Test
    public void testVipTier_NewUserIsNone() {
        when(installmentRepository.findByUserIdAndStatus(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        assertEquals(VipTier.NONE, loanService.getVipTier(1L));
    }

    @Test
    public void testVipTier_ManyPaidBecomesGold() {
        // 700 + (11*2) = 722 → GOLD
        List<LoanInstallment> paidList = Collections.nCopies(11,
                LoanInstallment.builder()
                        .loan(Loan.builder().user(businessUser).build())
                        .status(InstallmentStatus.PAID).build());

        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.PAID))
                .thenReturn(paidList);
        when(installmentRepository.findByUserIdAndStatus(1L, InstallmentStatus.OVERDUE))
                .thenReturn(Collections.emptyList());

        assertEquals(VipTier.GOLD, loanService.getVipTier(1L));
    }


    @Test
    public void testMarkOverdueInstallments() {
        Loan loan = Loan.builder().user(businessUser).build();

        LoanInstallment pastDue = LoanInstallment.builder()
                .loan(loan).status(InstallmentStatus.PENDING)
                .dueDate(LocalDate.now().minusDays(1)).installmentNumber(1).build();

        LoanInstallment future = LoanInstallment.builder()
                .loan(loan).status(InstallmentStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(10)).installmentNumber(2).build();

        when(installmentRepository.findAllByStatus(InstallmentStatus.PENDING))
                .thenReturn(List.of(pastDue, future));

        loanService.markOverdueInstallments();

        assertEquals(InstallmentStatus.OVERDUE, pastDue.getStatus());
        assertEquals(InstallmentStatus.PENDING,  future.getStatus());
        verify(installmentRepository).saveAll(List.of(pastDue));
        verify(notificationService, times(1))
                .createNotification(eq(1L), anyString(), eq("LOAN"));
    }


    @Test
    public void testPreCloseLoan() {
        Loan loan = Loan.builder()
                .loanId(1L).user(businessUser)
                .remainingAmount(BigDecimal.valueOf(10000))
                .status(LoanStatus.ACTIVE).build();

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        loanService.preCloseLoan(1L, 1L);

        verify(walletService).withdrawFunds(1L, new BigDecimal("10200.00"));
        assertEquals(LoanStatus.CLOSED, loan.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(loan.getRemainingAmount()));
    }

    @Test(expected = RuntimeException.class)
    public void testPreCloseLoan_UnauthorizedUser() {
        Loan loan = Loan.builder()
                .loanId(1L).user(businessUser)
                .status(LoanStatus.ACTIVE).build();

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        loanService.preCloseLoan(99L, 1L); // Different userId
    }
}