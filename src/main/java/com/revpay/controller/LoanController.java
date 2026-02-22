package com.revpay.controller;

import com.revpay.model.dto.*;
import com.revpay.model.entity.Loan;
import com.revpay.model.entity.LoanInstallment;
import com.revpay.repository.UserRepository;
import com.revpay.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;
    private final UserRepository userRepository;

    private Long getUserId(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getUserId();
    }

    @PostMapping("/apply")
    public ResponseEntity<LoanResponseDTO> applyLoan(
            @Valid @RequestBody LoanApplyDTO dto,
            Authentication auth) {

        return ResponseEntity.ok(loanService.applyLoan(getUserId(auth), dto));
    }

    @PostMapping("/repay")
    public ResponseEntity<String> repayLoan(
            @Valid @RequestBody LoanRepayDTO dto,
            Authentication auth) {

        return ResponseEntity.ok(loanService.repayLoan(getUserId(auth), dto));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Loan>> myLoans(Authentication auth) {
        return ResponseEntity.ok(loanService.getUserLoans(getUserId(auth)));
    }

    @GetMapping("/outstanding")
    public ResponseEntity<BigDecimal> totalOutstanding(Authentication auth) {
        return ResponseEntity.ok(loanService.totalOutstanding(getUserId(auth)));
    }

    @GetMapping("/emi/{loanId}")
    public ResponseEntity<List<LoanInstallment>> viewEmiSchedule(
            @PathVariable Long loanId,
            Authentication auth) {

        return ResponseEntity.ok(loanService.getEmiSchedule(getUserId(auth), loanId));
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<LoanInstallment>> getOverdues(Authentication auth) {
        return ResponseEntity.ok(loanService.getOverdueEmis(getUserId(auth)));
    }

    @GetMapping("/analytics")
    public ResponseEntity<LoanAnalyticsDTO> getAnalytics(Authentication auth) {
        Long userId = getUserId(auth);
        LoanAnalyticsDTO analytics = LoanAnalyticsDTO.builder()
                .totalOutstanding(loanService.totalOutstanding(userId))
                .totalPaid(loanService.totalPaid(userId))
                .totalPending(loanService.totalPending(userId))
                .build();
        return ResponseEntity.ok(analytics);
    }

    @PostMapping("/preclose/{loanId}")
    public ResponseEntity<String> preCloseLoan(
            @PathVariable Long loanId,
            Authentication auth) {

        return ResponseEntity.ok(loanService.preCloseLoan(getUserId(auth), loanId));
    }

    @GetMapping("/credit-score")
    public ResponseEntity<Integer> getCreditScore(Authentication auth) {
        return ResponseEntity.ok(loanService.calculateCreditScore(getUserId(auth)));
    }

    @GetMapping("/eligibility")
    public ResponseEntity<LoanEligibilityDTO> checkEligibility(Authentication auth) {
        return ResponseEntity.ok(loanService.checkEligibility(getUserId(auth)));
    }

    @GetMapping("/recommendation")
    public ResponseEntity<LoanRecommendationDTO> getRecommendation(Authentication auth) {
        return ResponseEntity.ok(loanService.getLoanRecommendation(getUserId(auth)));
    }
}