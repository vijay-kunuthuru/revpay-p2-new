import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../admin.service';

@Component({
  standalone: true,
    selector: 'app-admin-loans',
    imports: [CommonModule, FormsModule],
    templateUrl: './admin-loans.component.html',
    styleUrl: './admin-loans.component.css'
})
export class AdminLoansComponent implements OnInit {
  loans: any[] = [];
  loading = false;
  error = '';

  currentPage = 0;
  pageSize = 10;
  totalPages = 0;

  constructor(private adminService: AdminService) { }

  ngOnInit() {
    this.loadLoans();
  }

  loadLoans(page: number = 0) {
    this.loading = true;
    this.error = '';
    this.currentPage = page;

    this.adminService.getAllLoans(this.currentPage, this.pageSize).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.loans = res.data.content;
          // Set default prop rate to 8.5% for ease of use
          this.loans.forEach(loan => {
            if (loan.status === 'PENDING' || loan.status === 'APPLIED') loan.proposedRate = 8.5;
          });
          this.totalPages = res.data.totalPages;
        } else {
          this.error = res.message || 'Failed to load loans';
        }
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.message || 'Error communicating with server';
        this.loading = false;
      }
    });
  }

  processLoan(loan: any, isApproved: boolean) {
    const payload: any = {
      loanId: loan.loanId,
      approved: isApproved
    };

    if (isApproved) {
      if (!loan.proposedRate || loan.proposedRate <= 0) {
        alert("Please provide a valid interest rate to approve the loan.");
        return;
      }
      payload.approvedInterestRate = loan.proposedRate;
    } else {
      payload.rejectionReason = "Admin rejected the application.";
    }

    this.adminService.approveLoan(payload).subscribe({
      next: (res) => {
        if (res.success) {
          alert(`Loan successfully ${isApproved ? 'approved' : 'rejected'}.`);
          this.loadLoans(this.currentPage);
        } else {
          alert("Error: " + res.message);
        }
      },
      error: (err) => alert("Failed to process loan: " + (err.error?.message || 'Unknown error'))
    });
  }

  nextPage() {
    if (this.currentPage < this.totalPages - 1) {
      this.loadLoans(this.currentPage + 1);
    }
  }

  prevPage() {
    if (this.currentPage > 0) {
      this.loadLoans(this.currentPage - 1);
    }
  }
}
