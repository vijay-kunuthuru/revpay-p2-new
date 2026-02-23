package com.revpay.controller;

import com.revpay.model.dto.*;
import com.revpay.model.entity.Loan;
import com.revpay.model.entity.LoanInstallment;
import com.revpay.security.UserDetailsImpl;
import com.revpay.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'BUSINESS')") // Secures all endpoints for valid account holders
@Tag(name = "User Loan Operations", description = "Endpoints for users to apply for, manage, and repay loans, as well as view credit analytics")
public class LoanController {

    private final LoanService loanService;

    // --- DTOs ---
    public record LoanDTO(Long loanId, Long userId, BigDecimal amount, BigDecimal interestRate, Integer tenureMonths, BigDecimal emiAmount, BigDecimal remainingAmount, String purpose, String status, LocalDate startDate, LocalDate endDate) {}
    public record InstallmentDTO(Long installmentId, Long loanId, Integer installmentNumber, BigDecimal amount, LocalDate dueDate, String status) {}

    // Helper method to extract the authenticated user's ID
    private Long getUserId(Authentication auth) {
        return ((UserDetailsImpl) auth.getPrincipal()).getUserId();
    }

    // --- ENDPOINTS ---

    @PostMapping("/apply")
    @Operation(summary = "Apply for a new loan", description = "Submits a new loan application for the authenticated user.")
    public ResponseEntity<ApiResponse<LoanResponseDTO>> applyLoan(
            @Valid @RequestBody LoanApplyDTO dto,
            Authentication auth) {

        Long userId = getUserId(auth);
        log.info("User ID: {} is applying for a loan of amount: {}", userId, dto.getAmount());

        LoanResponseDTO response = loanService.applyLoan(userId, dto);

        log.info("Loan application submitted successfully for User ID: {}", userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Loan application submitted successfully"));
    }

    @PostMapping("/repay")
    @Operation(summary = "Repay loan installment", description = "Processes a repayment towards an active loan's EMI or outstanding balance.")
    public ResponseEntity<ApiResponse<String>> repayLoan(
            @Valid @RequestBody LoanRepayDTO dto,
            Authentication auth) {

        Long userId = getUserId(auth);
        log.info("User ID: {} is initiating a loan repayment for Loan ID: {}", userId, dto.getLoanId());

        String result = loanService.repayLoan(userId, dto);

        return ResponseEntity.ok(ApiResponse.success(result, "Loan repayment processed successfully"));
    }

    @GetMapping("/my")
    @Operation(summary = "Get my loans", description = "Retrieves a paginated list of all loans belonging to the authenticated user.")
    public ResponseEntity<ApiResponse<Page<LoanDTO>>> myLoans(
            Authentication auth,
            @PageableDefault(size = 10) Pageable pageable) {

        Long userId = getUserId(auth);
        log.debug("Fetching loans for User ID: {}. Page: {}, Size: {}", userId, pageable.getPageNumber(), pageable.getPageSize());

        Page<Loan> loans = loanService.getUserLoansPaged(userId, pageable);

        Page<LoanDTO> dtos = loans.map(l -> new LoanDTO(
                l.getLoanId(),
                l.getUser().getUserId(),
                l.getAmount(),
                l.getInterestRate(),
                l.getTenureMonths(),
                l.getEmiAmount(),
                l.getRemainingAmount(),
                l.getPurpose(),
                l.getStatus().name(),
                l.getStartDate(),
                l.getEndDate()
        ));

        return ResponseEntity.ok(ApiResponse.success(dtos, "User loans retrieved successfully"));
    }

    @GetMapping("/outstanding")
    @Operation(summary = "Get total outstanding amount", description = "Calculates the total remaining amount across all active loans for the user.")
    public ResponseEntity<ApiResponse<BigDecimal>> totalOutstanding(Authentication auth) {
        BigDecimal total = loanService.totalOutstanding(getUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(total, "Total outstanding amount retrieved"));
    }

    @GetMapping("/emi/{loanId}")
    @Operation(summary = "View EMI schedule", description = "Retrieves the paginated EMI schedule (installments) for a specific loan.")
    public ResponseEntity<ApiResponse<Page<InstallmentDTO>>> viewEmiSchedule(
            @Parameter(description = "ID of the loan") @PathVariable Long loanId,
            Authentication auth,
            @PageableDefault(size = 12) Pageable pageable) {

        log.debug("Fetching EMI schedule for Loan ID: {}", loanId);

        Page<LoanInstallment> installments = loanService.getEmiSchedulePaged(getUserId(auth), loanId, pageable);

        Page<InstallmentDTO> dtos = installments.map(i -> new InstallmentDTO(
                i.getInstallmentId(),
                i.getLoan().getLoanId(),
                i.getInstallmentNumber(),
                i.getAmount(),
                i.getDueDate(),
                i.getStatus().name()
        ));

        return ResponseEntity.ok(ApiResponse.success(dtos, "EMI schedule retrieved successfully"));
    }

    @GetMapping("/overdue")
    @Operation(summary = "Get overdue EMIs", description = "Retrieves a paginated list of all overdue EMI installments for the user.")
    public ResponseEntity<ApiResponse<Page<InstallmentDTO>>> getOverdues(
            Authentication auth,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<LoanInstallment> overdues = loanService.getOverdueEmisPaged(getUserId(auth), pageable);

        Page<InstallmentDTO> dtos = overdues.map(i -> new InstallmentDTO(
                i.getInstallmentId(),
                i.getLoan().getLoanId(),
                i.getInstallmentNumber(),
                i.getAmount(),
                i.getDueDate(),
                i.getStatus().name()
        ));

        return ResponseEntity.ok(ApiResponse.success(dtos, "Overdue EMIs retrieved successfully"));
    }

    @GetMapping("/analytics")
    @Operation(summary = "Get loan analytics", description = "Retrieves high-level analytics including total outstanding, total paid, and pending amounts.")
    public ResponseEntity<ApiResponse<LoanAnalyticsDTO>> getAnalytics(Authentication auth) {
        Long userId = getUserId(auth);

        LoanAnalyticsDTO dto = LoanAnalyticsDTO.builder()
                .totalOutstanding(loanService.totalOutstanding(userId))
                .totalPaid(loanService.totalPaid(userId))
                .totalPending(loanService.totalPending(userId))
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto, "Loan analytics retrieved successfully"));
    }

    @PostMapping("/preclose/{loanId}")
    @Operation(summary = "Pre-close loan", description = "Initiates the pre-closure process for a specific loan, calculating any applicable pre-closure charges.")
    public ResponseEntity<ApiResponse<String>> preCloseLoan(
            @Parameter(description = "ID of the loan to pre-close") @PathVariable Long loanId,
            Authentication auth) {

        Long userId = getUserId(auth);
        log.info("User ID: {} is initiating pre-closure for Loan ID: {}", userId, loanId);

        String result = loanService.preCloseLoan(userId, loanId);

        return ResponseEntity.ok(ApiResponse.success(result, "Loan pre-closure processed successfully"));
    }

    @GetMapping("/credit-score")
    @Operation(summary = "Get user credit score", description = "Calculates and returns the user's current internal platform credit score.")
    public ResponseEntity<ApiResponse<Integer>> getCreditScore(Authentication auth) {
        Integer score = loanService.calculateCreditScore(getUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(score, "Credit score retrieved successfully"));
    }

    @GetMapping("/eligibility")
    @Operation(summary = "Check loan eligibility", description = "Evaluates the user's financial profile to determine their loan eligibility constraints.")
    public ResponseEntity<ApiResponse<LoanEligibilityDTO>> checkEligibility(Authentication auth) {
        LoanEligibilityDTO dto = loanService.checkEligibility(getUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(dto, "Loan eligibility retrieved successfully"));
    }

    @GetMapping("/recommendation")
    @Operation(summary = "Get loan recommendation", description = "Provides AI-driven loan recommendations based on the user's current financial standing and credit score.")
    public ResponseEntity<ApiResponse<LoanRecommendationDTO>> getRecommendation(Authentication auth) {
        LoanRecommendationDTO dto = loanService.getLoanRecommendation(getUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(dto, "Loan recommendation retrieved successfully"));
    }
}