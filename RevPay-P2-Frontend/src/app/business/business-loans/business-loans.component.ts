import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { LoanService, LoanDTO, LoanAnalyticsDTO } from '../loan.service';
import { BusinessProfileService } from '../business-profile.service';

@Component({
  standalone: true,
    selector: 'app-business-loans',
    imports: [CommonModule, ReactiveFormsModule],
    templateUrl: './business-loans.component.html',
    styleUrl: './business-loans.component.css'
})
export class BusinessLoansComponent implements OnInit {
  isVerified = false;
  showApplyForm = false;
  loanForm: FormGroup;

  analytics: LoanAnalyticsDTO | null = null;
  loans: LoanDTO[] = [];

  constructor(
    private loanService: LoanService,
    private profileService: BusinessProfileService,
    private fb: FormBuilder
  ) {
    this.loanForm = this.fb.group({
      amount: ['', [Validators.required, Validators.min(1000)]],
      tenureMonths: ['', [Validators.required, Validators.min(3), Validators.max(60)]],
      purpose: ['', Validators.required],
      loanType: ['WORKING_CAPITAL', Validators.required]
    });
  }

  ngOnInit() {
    this.profileService.getMyProfile().subscribe({
      next: (res) => {
        this.isVerified = res.data.isVerified;
        if (this.isVerified) {
          this.loadDashboardData();
        }
      },
      error: (err) => console.error(err)
    });
  }

  loadDashboardData() {
    // Load Analytics
    this.loanService.getAnalytics().subscribe({
      next: (res) => this.analytics = res.data,
      error: (err) => console.error(err)
    });

    // Load Loans
    this.loanService.getMyLoans(0, 50).subscribe({
      next: (res) => this.loans = res.data.content,
      error: (err) => console.error(err)
    });
  }

  applyForLoan() {
    if (this.loanForm.valid) {
      const payload = { ...this.loanForm.value, idempotencyKey: Date.now().toString() };
      this.loanService.applyForLoan(payload).subscribe({
        next: () => {
          this.showApplyForm = false;
          this.loanForm.reset();
          this.loadDashboardData();
          alert("Loan application submitted successfully and is pending admin approval!");
        },
        error: (err) => alert("Failed to apply for loan: " + (err.error?.message || "Error"))
      });
    }
  }

  repayEmi(loanId: number, emiAmount: number) {
    if (confirm(`Pay the scheduled Monthly EMI of ₹${emiAmount.toFixed(2)} from your wallet balance?`)) {
      const pin = prompt("Please enter your transaction PIN to confirm EMI payment:");
      if (!pin) {
        alert("Payment cancelled: PIN is required.");
        return;
      }

      this.loanService.repayLoan(loanId, emiAmount, pin, false).subscribe({
        next: () => {
          this.loadDashboardData();
          alert("Monthly EMI paid successfully!");
        },
        error: (err) => alert("Failed to pay EMI: " + (err.error?.message || "Insufficient balance or error"))
      });
    }
  }

  precloseLoan(loanId: number, remainingAmount: number) {
    if (confirm(`Are you sure you want to preclose this loan? This will deduct the remaining balance of ₹${remainingAmount.toFixed(2)} from your wallet.`)) {
      const pin = prompt("Please enter your transaction PIN to confirm Loan Preclosure:");
      if (!pin) {
        alert("Payment cancelled: PIN is required.");
        return;
      }

      this.loanService.repayLoan(loanId, remainingAmount, pin, true).subscribe({
        next: () => {
          this.loadDashboardData();
          alert("Loan preclosed successfully!");
        },
        error: (err) => alert("Failed to preclose loan: " + (err.error?.message || "Insufficient balance or error"))
      });
    }
  }
}
