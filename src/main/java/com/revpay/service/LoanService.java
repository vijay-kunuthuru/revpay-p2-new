package com.revpay.service;

import com.revpay.model.dto.*;
import com.revpay.model.entity.*;
import com.revpay.repository.LoanInstallmentRepository;
import com.revpay.repository.LoanRepository;
import com.revpay.repository.UserRepository;
import com.revpay.util.EmiCalculator;
import com.revpay.util.NotificationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class LoanService {

    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final LoanInstallmentRepository installmentRepository;
    private final NotificationService notificationService;

    private static final BigDecimal PRE_CLOSURE_RATE = new BigDecimal("0.02");
    private static final BigDecimal MEDIUM_RISK_FACTOR = new BigDecimal("0.7");
    private static final BigDecimal HIGH_RISK_FACTOR = new BigDecimal("0.4");
    private static final BigDecimal OVERDUE_PENALTY = new BigDecimal("100");
    private static final BigDecimal MIN_INTEREST_RATE = new BigDecimal("1");

    @Transactional
    public LoanResponseDTO applyLoan(Long userId, LoanApplyDTO dto) {
        log.info("Applying loan for userId: {}, amount: {}", userId, dto.getAmount());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRole() != Role.BUSINESS) {
            throw new IllegalArgumentException("Only business users can apply for loans");
        }

        log.debug("Checking eligibility for userId: {}", userId);
        if (!isEligibleForLoan(userId, dto.getAmount())) {
            throw new IllegalArgumentException("Loan amount exceeds eligibility limit");
        }

        Loan loan = Loan.builder()
                .user(user)
                .amount(dto.getAmount())
                .tenureMonths(dto.getTenureMonths())
                .remainingAmount(dto.getAmount())
                .purpose(dto.getPurpose())
                .status(LoanStatus.APPLIED)
                .build();

        loanRepository.save(loan);
        log.info("Loan application saved with id: {}", loan.getLoanId());

        User admin = userRepository.findByRole(Role.ADMIN).stream().findFirst().orElse(null);
        if (admin != null) {
            notificationService.createNotification(
                    admin.getUserId(),
                    "New loan application of ₹" + dto.getAmount() + " from " + user.getFullName(),
                    "LOAN_REQUEST");
        }

        notificationService.createNotification(
                user.getUserId(),
                NotificationUtil.loanApplied(dto.getAmount()),
                "LOAN");

        return LoanResponseDTO.builder()
                .loanId(loan.getLoanId())
                .amount(loan.getAmount())
                .emiAmount(BigDecimal.ZERO)
                .remainingAmount(loan.getRemainingAmount())
                .status(loan.getStatus())
                .build();
    }

    @Transactional
    public String approveLoan(Long adminId, LoanApprovalDTO dto) {
        log.info("Loan approval process started for loanId: {}", dto.getLoanId());

        Loan loan = loanRepository.findById(dto.getLoanId())
                .orElseThrow(() -> {
                    log.error("Loan not found for loanId: {}", dto.getLoanId());
                    return new IllegalArgumentException("Loan not found");
                });

        if (loan.getStatus() == LoanStatus.ACTIVE || loan.getStatus() == LoanStatus.CLOSED) {
            throw new IllegalArgumentException("Loan is already " + loan.getStatus());
        }

        if (dto.getApproved()) {
            log.info("Loan approved for loanId: {}", dto.getLoanId());

            BigDecimal interest = (dto.getInterestRate() != null)
                    ? dto.getInterestRate()
                    : getDynamicInterest(loan.getUser().getUserId());

            log.debug("Interest rate for loanId {}: {}", dto.getLoanId(), interest);

            loan.setInterestRate(interest);
            loan.setStatus(LoanStatus.ACTIVE);
            loan.setStartDate(LocalDate.now());
            loan.setEndDate(LocalDate.now().plusMonths(loan.getTenureMonths()));

            BigDecimal emi = EmiCalculator.calculateEMI(
                    loan.getAmount(), interest, loan.getTenureMonths());
            loan.setEmiAmount(emi);

            log.info("EMI calculated for loanId {}: {}", dto.getLoanId(), emi);

            walletService.disburseLoanFromAdmin(
                    adminId,
                    loan.getUser().getUserId(),
                    loan.getAmount(),
                    "Loan Disbursement #" + loan.getLoanId());
            log.info("Loan amount disbursed to wallet for userId: {}", loan.getUser().getUserId());

            generateInstallments(loan);
            log.info("EMI schedule generated for loanId: {}", dto.getLoanId());

            notificationService.createNotification(
                    loan.getUser().getUserId(),
                    NotificationUtil.loanApproved(),
                    "LOAN");

        } else {
            log.warn("Loan rejected for loanId: {}", dto.getLoanId());
            loan.setStatus(LoanStatus.REJECTED);

            notificationService.createNotification(
                    loan.getUser().getUserId(),
                    NotificationUtil.loanRejected(),
                    "LOAN");
        }

        loanRepository.save(loan);
        log.info("Loan status updated successfully for loanId: {}", dto.getLoanId());

        return "Loan decision processed";
    }

    private void generateInstallments(Loan loan) {
        log.info("Generating EMI installments for loanId: {}", loan.getLoanId());

        for (int i = 1; i <= loan.getTenureMonths(); i++) {
            LoanInstallment installment = LoanInstallment.builder()
                    .loan(loan)
                    .installmentNumber(i)
                    .amount(loan.getEmiAmount())
                    .dueDate(LocalDate.now().plusMonths(i))
                    .status(InstallmentStatus.PENDING)
                    .build();
            installmentRepository.save(installment);
            log.debug("Installment {} created with due date {} for loanId: {}",
                    i, installment.getDueDate(), loan.getLoanId());
        }

        log.info("All {} EMI installments generated for loanId: {}",
                loan.getTenureMonths(), loan.getLoanId());
    }

    @Transactional
    public String repayLoan(Long userId, LoanRepayDTO dto) {
        log.info("Loan repayment initiated for loanId: {} by userId: {}", dto.getLoanId(), userId);

        Loan loan = loanRepository.findById(dto.getLoanId())
                .orElseThrow(() -> {
                    log.error("Loan not found for loanId: {}", dto.getLoanId());
                    return new IllegalArgumentException("Loan not found");
                });

        if (!loan.getUser().getUserId().equals(userId)) {
            log.warn("Unauthorized repayment attempt by userId: {} for loanId: {}", userId, dto.getLoanId());
            throw new IllegalArgumentException("Unauthorized repayment attempt");
        }

        if (loan.getStatus() == LoanStatus.CLOSED) {
            throw new IllegalArgumentException("Loan is already fully repaid");
        }

        LoanInstallment nextInstallment = installmentRepository
                .findByLoan_LoanId(loan.getLoanId())
                .stream()
                .filter(i -> i.getStatus() == InstallmentStatus.PENDING
                        || i.getStatus() == InstallmentStatus.OVERDUE)
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("No pending EMI found for loanId: {}", loan.getLoanId());
                    return new IllegalArgumentException("No pending EMI");
                });

        BigDecimal emiAmount = nextInstallment.getAmount();
        BigDecimal payableAmount = emiAmount;
        BigDecimal penalty = BigDecimal.ZERO;

        if (nextInstallment.getStatus() == InstallmentStatus.OVERDUE) {
            penalty = OVERDUE_PENALTY;
            payableAmount = emiAmount.add(penalty);
            log.warn("Overdue EMI detected. Penalty of ₹100 applied for loanId: {}", loan.getLoanId());
        }

        walletService.withdrawFundsForLoan(
                userId,
                payableAmount,
                "Loan Repayment #" + loan.getLoanId());
        log.info("Amount {} deducted from wallet for userId: {}", payableAmount, userId);

        nextInstallment.setStatus(InstallmentStatus.PAID);
        installmentRepository.save(nextInstallment);
        log.info("Installment {} marked as PAID for loanId: {}",
                nextInstallment.getInstallmentNumber(), loan.getLoanId());

        loan.setRemainingAmount(loan.getRemainingAmount().subtract(payableAmount));

        if (loan.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setRemainingAmount(BigDecimal.ZERO);
            loan.setStatus(LoanStatus.CLOSED);
            log.info("Loan closed successfully for loanId: {}", loan.getLoanId());
        }

        loanRepository.save(loan);

        notificationService.createNotification(
                loan.getUser().getUserId(),
                NotificationUtil.loanRepayment(payableAmount),
                "LOAN");
        log.info("Repayment notification sent for loanId: {}", loan.getLoanId());

        User admin = userRepository.findByRole(Role.ADMIN).stream().findFirst().orElse(null);
        if (admin != null) {
            notificationService.createNotification(
                    admin.getUserId(),
                    "Received loan EMI payment of ₹" + payableAmount + " from " + loan.getUser().getFullName(),
                    "LOAN_REPAYMENT");
        }

        return "EMI Paid Successfully";
    }

    @Transactional
    public String preCloseLoan(Long userId, Long loanId) {
        log.info("Pre-closure request initiated for loanId: {} by userId: {}", loanId, userId);

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> {
                    log.error("Loan not found for loanId: {}", loanId);
                    return new IllegalArgumentException("Loan not found");
                });

        if (!loan.getUser().getUserId().equals(userId)) {
            log.warn("Unauthorized pre-closure attempt by userId: {} for loanId: {}", userId, loanId);
            throw new IllegalArgumentException("Unauthorized");
        }

        if (loan.getStatus() == LoanStatus.CLOSED) {
            throw new IllegalArgumentException("Loan is already closed");
        }

        BigDecimal remaining = loan.getRemainingAmount();

        BigDecimal preClosureCharge = remaining.multiply(PRE_CLOSURE_RATE);
        BigDecimal totalPayable = remaining.add(preClosureCharge);

        log.debug("Remaining: {}, Pre-closure charge: {}, Total payable: {}",
                remaining, preClosureCharge, totalPayable);

        walletService.withdrawFundsForLoan(
                userId,
                totalPayable,
                "Loan Pre-Closure #" + loanId);
        log.info("Amount {} deducted for loan pre-closure for userId: {}", totalPayable, userId);

        loan.setRemainingAmount(BigDecimal.ZERO);
        loan.setStatus(LoanStatus.CLOSED);
        loanRepository.save(loan);
        
        List<LoanInstallment> installments = installmentRepository.findByLoan_LoanId(loanId);
        installments.stream()
                .filter(i -> i.getStatus() == InstallmentStatus.PENDING || i.getStatus() == InstallmentStatus.OVERDUE)
                .forEach(i -> i.setStatus(InstallmentStatus.CANCELLED));
        installmentRepository.saveAll(installments);
        
        log.info("Loan successfully pre-closed for loanId: {}", loanId);

        notificationService.createNotification(
                loan.getUser().getUserId(),
                "Your loan has been pre-closed successfully.",
                "LOAN");
        log.info("Pre-closure notification sent to userId: {}", loan.getUser().getUserId());

        User admin = userRepository.findByRole(Role.ADMIN).stream().findFirst().orElse(null);
        if (admin != null) {
            notificationService.createNotification(
                    admin.getUserId(),
                    "Loan pre-closure payment of ₹" + totalPayable + " received from " + loan.getUser().getFullName(),
                    "LOAN_REPAYMENT");
        }

        return "Loan Pre-closed successfully";
    }

    @Transactional(readOnly = true)
    public List<Loan> getUserLoans(Long userId) {
        log.info("Fetching loans for userId: {}", userId);
        List<Loan> loans = loanRepository.findByUser_UserId(userId);
        log.debug("Total loans found for userId {}: {}", userId, loans.size());
        return loans;
    }

    @Transactional(readOnly = true)
    public Page<Loan> getUserLoansPaged(Long userId, Pageable pageable) {
        log.info("Fetching paginated loans for userId: {}", userId);
        return loanRepository.findByUser_UserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalOutstanding(Long userId) {
        log.info("Calculating total outstanding amount for userId: {}", userId);
        BigDecimal total = loanRepository.findByUser_UserId(userId)
                .stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE)
                .map(Loan::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.debug("Total outstanding for userId {}: {}", userId, total);
        return total;
    }

    @Transactional(readOnly = true)
    public List<Loan> getAllLoans() {
        log.info("Fetching all loans from system");
        List<Loan> loans = loanRepository.findAll();
        log.debug("Total loans in system: {}", loans.size());
        return loans;
    }

    @Transactional(readOnly = true)
    public Page<Loan> getAllLoansPaged(Pageable pageable) {
        log.info("Fetching all loans from system (paginated)");
        return loanRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<LoanInstallment> getEmiSchedule(Long userId, Long loanId) {
        log.info("Fetching EMI schedule for loanId: {} by userId: {}", loanId, userId);

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> {
                    log.error("Loan not found for loanId: {}", loanId);
                    return new IllegalArgumentException("Loan not found");
                });

        if (!loan.getUser().getUserId().equals(userId)) {
            log.warn("Unauthorized EMI schedule access by userId: {} for loanId: {}", userId, loanId);
            throw new IllegalArgumentException("Unauthorized");
        }

        List<LoanInstallment> installments = installmentRepository.findByLoan_LoanId(loanId);
        log.debug("Total EMI installments fetched for loanId {}: {}", loanId, installments.size());
        return installments;
    }

    @Transactional(readOnly = true)
    public Page<LoanInstallment> getEmiSchedulePaged(Long userId, Long loanId, Pageable pageable) {
        log.info("Fetching paginated EMI schedule for loanId: {} by userId: {}", loanId, userId);

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (!loan.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized");
        }

        return installmentRepository.findByLoan_LoanId(loanId, pageable);
    }

    @Transactional(readOnly = true)
    public List<LoanInstallment> getOverdueEmis(Long userId) {
        log.info("Fetching overdue EMIs for userId: {}", userId);

        List<LoanInstallment> overdueList = new ArrayList<>(
                installmentRepository.findByUserIdAndStatus(userId, InstallmentStatus.OVERDUE)
                        .stream()
                        .filter(i -> i.getLoan().getStatus() == com.revpay.model.entity.LoanStatus.ACTIVE)
                        .toList());

        installmentRepository.findByUserIdAndStatus(userId, InstallmentStatus.PENDING)
                .stream()
                .filter(i -> i.getLoan().getStatus() == com.revpay.model.entity.LoanStatus.ACTIVE)
                .filter(i -> i.getDueDate().isBefore(LocalDate.now()))
                .forEach(overdueList::add);

        log.debug("Total overdue EMIs for userId {}: {}", userId, overdueList.size());
        return overdueList;
    }

    @Transactional(readOnly = true)
    public Page<LoanInstallment> getOverdueEmisPaged(Long userId, Pageable pageable) {
        log.info("Fetching paginated overdue EMIs for userId: {}", userId);
        
        List<LoanInstallment> allActiveOverdues = getOverdueEmis(userId);
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allActiveOverdues.size());
        
        List<LoanInstallment> pageContent = (start <= end && start < allActiveOverdues.size()) 
                ? allActiveOverdues.subList(start, end) 
                : List.of();
                
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, allActiveOverdues.size());
    }

    @Transactional(readOnly = true)
    public BigDecimal totalPaid(Long userId) {
        log.info("Calculating total paid EMI amount for userId: {}", userId);

        BigDecimal total = installmentRepository
                .findByUserIdAndStatus(userId, InstallmentStatus.PAID)
                .stream()
                .map(LoanInstallment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug("Total paid EMI for userId {}: {}", userId, total);
        return total;
    }

    @Transactional(readOnly = true)
    public BigDecimal totalPending(Long userId) {
        log.info("Calculating total pending EMI amount for userId: {}", userId);

        BigDecimal total = installmentRepository
                .findByUserIdAndStatus(userId, InstallmentStatus.PENDING)
                .stream()
                .filter(i -> i.getLoan().getStatus() == com.revpay.model.entity.LoanStatus.ACTIVE)
                .map(LoanInstallment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug("Total pending EMI for userId {}: {}", userId, total);
        return total;
    }

    @Transactional
    public void markOverdueInstallments() {
        log.info("Starting overdue EMI check process");

        List<LoanInstallment> pendingInstallments = installmentRepository.findAllByStatus(InstallmentStatus.PENDING);

        log.debug("Total PENDING installments fetched: {}", pendingInstallments.size());

        List<LoanInstallment> nowOverdue = pendingInstallments.stream()
                .filter(i -> i.getDueDate().isBefore(LocalDate.now()))
                .toList();

        nowOverdue.forEach(emi -> {
            emi.setStatus(InstallmentStatus.OVERDUE);
            log.warn("EMI marked as OVERDUE for loanId: {}, installment: {}",
                    emi.getLoan().getLoanId(), emi.getInstallmentNumber());
        });

        installmentRepository.saveAll(nowOverdue);

        nowOverdue.forEach(emi -> notificationService.createNotification(
                emi.getLoan().getUser().getUserId(),
                "Your EMI is overdue. Please pay immediately.",
                "LOAN"));

        log.info("Overdue EMI check completed. {} installments marked overdue.", nowOverdue.size());
    }

    @Transactional(readOnly = true)
    public int calculateCreditScore(Long userId) {
        log.info("Calculating credit score for userId: {}", userId);

        long paid = installmentRepository.findByUserIdAndStatus(userId, InstallmentStatus.PAID).size();
        long overdue = installmentRepository.findByUserIdAndStatus(userId, InstallmentStatus.OVERDUE).size();

        log.debug("Paid installments: {}, Overdue installments: {}", paid, overdue);

        int score = 700 + (int) (paid * 2) - (int) (overdue * 5);
        score = Math.max(300, Math.min(850, score));

        log.info("Final credit score for userId {}: {}", userId, score);
        return score;
    }

    @Transactional(readOnly = true)
    public RiskTier getRiskTier(Long userId) {
        log.info("Evaluating risk tier for userId: {}", userId);
        int score = calculateCreditScore(userId);
        RiskTier tier = (score >= 750) ? RiskTier.LOW
                : (score >= 650) ? RiskTier.MEDIUM
                        : RiskTier.HIGH;
        log.debug("Risk tier for userId {} with score {}: {}", userId, score, tier);
        return tier;
    }

    @Transactional(readOnly = true)
    public BigDecimal getLoanLimit(Long userId) {
        log.info("Calculating loan limit for userId: {}", userId);
        int score = calculateCreditScore(userId);
        BigDecimal limit = (score >= 750) ? BigDecimal.valueOf(1_000_000)
                : (score >= 700) ? BigDecimal.valueOf(500_000)
                        : (score >= 650) ? BigDecimal.valueOf(200_000)
                                : BigDecimal.valueOf(50_000);
        log.debug("Loan limit for userId {} with score {}: {}", userId, score, limit);
        return limit;
    }

    @Transactional(readOnly = true)
    public boolean isEligibleForLoan(Long userId, BigDecimal requestedAmount) {
        log.info("Checking loan eligibility for userId: {} with requested amount: {}", userId, requestedAmount);
        RiskTier risk = getRiskTier(userId);
        if (risk == RiskTier.HIGH) {
            log.warn("UserId {} is HIGH risk. Loan not eligible", userId);
            return false;
        }
        BigDecimal limit = getLoanLimit(userId);
        boolean eligible = requestedAmount.compareTo(limit) <= 0;
        log.debug("Eligibility check for userId {}: Requested {}, Limit {}, Eligible: {}",
                userId, requestedAmount, limit, eligible);
        return eligible;
    }

    @Transactional(readOnly = true)
    public LoanEligibilityDTO checkEligibility(Long userId) {
        log.info("Fetching loan eligibility details for userId: {}", userId);
        int score = calculateCreditScore(userId);
        RiskTier risk = getRiskTier(userId);
        BigDecimal limit = getLoanLimit(userId);
        boolean eligible = (risk != RiskTier.HIGH);
        log.debug("Eligibility result -> Score: {}, Risk: {}, Limit: {}, Eligible: {}",
                score, risk, limit, eligible);
        return LoanEligibilityDTO.builder()
                .creditScore(score)
                .riskTier(risk)
                .maxEligibleAmount(limit)
                .eligible(eligible)
                .build();
    }

    @Transactional(readOnly = true)
    public VipTier getVipTier(Long userId) {
        log.info("Evaluating VIP tier for userId: {}", userId);
        int score = calculateCreditScore(userId);
        VipTier tier = (score >= 780) ? VipTier.PLATINUM
                : (score >= 720) ? VipTier.GOLD
                        : VipTier.NONE;
        log.debug("VIP tier for userId {} with score {}: {}", userId, score, tier);
        return tier;
    }

    private BigDecimal getDynamicInterest(Long userId) {
        log.info("Calculating dynamic interest for userId: {}", userId);
        int score = calculateCreditScore(userId);
        BigDecimal interest = (score >= 750) ? BigDecimal.valueOf(8)
                : (score >= 700) ? BigDecimal.valueOf(10)
                        : (score >= 650) ? BigDecimal.valueOf(12)
                                : BigDecimal.valueOf(15);
        log.debug("Base interest based on score {}: {}", score, interest);
        BigDecimal finalInterest = applyVipDiscount(userId, interest);
        log.info("Final interest after VIP adjustment for userId {}: {}", userId, finalInterest);
        return finalInterest;
    }

    private BigDecimal applyVipDiscount(Long userId, BigDecimal baseInterest) {
        log.info("Applying VIP discount for userId: {}", userId);
        VipTier vip = getVipTier(userId);
        BigDecimal finalInterest = switch (vip) {
            case PLATINUM -> baseInterest.subtract(BigDecimal.valueOf(2));
            case GOLD -> baseInterest.subtract(BigDecimal.valueOf(1));
            default -> baseInterest;
        };

        if (finalInterest.compareTo(MIN_INTEREST_RATE) < 0)
            finalInterest = MIN_INTEREST_RATE;
        log.debug("VIP tier: {}, Base interest: {}, Final interest: {}", vip, baseInterest, finalInterest);
        return finalInterest;
    }

    @Transactional(readOnly = true)
    public LoanRecommendationDTO getLoanRecommendation(Long userId) {
        log.info("Generating loan recommendation for userId: {}", userId);

        int score = calculateCreditScore(userId);
        RiskTier risk = getRiskTier(userId);
        VipTier vip = getVipTier(userId);
        BigDecimal limit = getLoanLimit(userId);
        BigDecimal interest = getDynamicInterest(userId);

        // FIXED: Added default case to ensure the switch expression is exhaustive
        BigDecimal recommended = switch (risk) {
            case LOW -> limit;
            case MEDIUM -> limit.multiply(MEDIUM_RISK_FACTOR);
            case HIGH -> limit.multiply(HIGH_RISK_FACTOR);
            default -> limit.multiply(HIGH_RISK_FACTOR); // Fallback for safety
        };

        log.debug("Recommendation -> Score: {}, Risk: {}, VIP: {}, Limit: {}, Recommended: {}, Interest: {}",
                score, risk, vip, limit, recommended, interest);

        return LoanRecommendationDTO.builder()
                .creditScore(score)
                .riskTier(risk)
                .vipTier(vip)
                .recommendedAmount(recommended)
                // Synchronized with the DTO field name (Ensure this matches your latest DTO)
                .expectedInterestRate(interest)
                .logicReasoning("Recommendation based on internal credit score of " + score)
                .build();
    }
}