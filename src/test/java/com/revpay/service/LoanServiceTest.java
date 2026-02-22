package com.revpay.service;

import com.revpay.model.dto.LoanApplyDTO;
import com.revpay.model.dto.LoanApprovalDTO;
import com.revpay.model.dto.LoanRepayDTO;
import com.revpay.model.dto.LoanResponseDTO;
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

    @Test
    public void testApplyLoanSuccess() {

        LoanApplyDTO dto = new LoanApplyDTO();
        dto.setAmount(BigDecimal.valueOf(50000));
        dto.setTenureMonths(12);
        dto.setPurpose("Business Expansion");

        when(userRepository.findById(1L)).thenReturn(Optional.of(businessUser));

        LoanResponseDTO response = loanService.applyLoan(1L, dto);

        assertEquals(BigDecimal.valueOf(50000), response.getAmount());
        assertEquals(LoanStatus.APPLIED, response.getStatus());

        verify(loanRepository, times(1)).save(any(Loan.class));
        verify(notificationService, times(1))
                .createNotification(eq(1L),
                        eq(NotificationUtil.loanApplied(dto.getAmount())),
                        eq("LOAN"));
    }

    @Test(expected = RuntimeException.class)
    public void testApplyLoanFailsForNonBusinessUser() {

        User personalUser = new User();
        personalUser.setUserId(2L);
        personalUser.setRole(Role.PERSONAL);

        when(userRepository.findById(2L)).thenReturn(Optional.of(personalUser));

        LoanApplyDTO dto = new LoanApplyDTO();
        dto.setAmount(BigDecimal.valueOf(10000));

        loanService.applyLoan(2L, dto);
    }

    @Test
    public void testApproveLoan() {

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

        loanService.approveLoan(dto);
        verify(walletService).addFunds(eq(1L), any(), eq("Loan Disbursement"));
        verify(notificationService).createNotification(
                eq(1L),
                eq(NotificationUtil.loanApproved()),
                eq("LOAN")
        );
    }

    @Test
    public void testRepayLoan() {

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
                .build();

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(installmentRepository.findByLoan_LoanId(1L))
                .thenReturn(java.util.List.of(emi));

        LoanRepayDTO dto = new LoanRepayDTO();
        dto.setLoanId(1L);

        String result = loanService.repayLoan(1L, dto);

        assertEquals("EMI Paid Successfully", result);
        verify(walletService).withdrawFunds(1L, BigDecimal.valueOf(1000));
    }

    @Test
    public void testCreditScoreCalculation() {

        Loan loan = Loan.builder().user(businessUser).build();

        LoanInstallment paid = LoanInstallment.builder()
                .loan(loan)
                .status(InstallmentStatus.PAID)
                .build();

        LoanInstallment overdue = LoanInstallment.builder()
                .loan(loan)
                .status(InstallmentStatus.OVERDUE)
                .build();

        when(installmentRepository.findAll())
                .thenReturn(java.util.List.of(paid, overdue));

        int score = loanService.calculateCreditScore(1L);

        assertTrue(score >= 300 && score <= 850);
    }

    @Test
    public void testLoanEligibility() {

        boolean eligible = loanService.isEligibleForLoan(1L, BigDecimal.valueOf(10000));

        assertTrue(eligible);
    }

    @Test
    public void testVipTier() {

        VipTier tier = loanService.getVipTier(1L);

        assertNotNull(tier);
    }
}