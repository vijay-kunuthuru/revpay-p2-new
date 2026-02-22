package com.revpay.controller;

import com.revpay.model.dto.LoanApprovalDTO;
import com.revpay.model.entity.Loan;
import com.revpay.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/loans")
@RequiredArgsConstructor
public class LoanAdminController {

    private final LoanService loanService;

    @PostMapping("/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> approveLoan(@Valid @RequestBody LoanApprovalDTO dto) {
        return ResponseEntity.ok(loanService.approveLoan(dto));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Loan>> getAllLoans() {
        return ResponseEntity.ok(loanService.getAllLoans());
    }
}