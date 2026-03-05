import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { AdminService } from '../admin.service';

@Component({
  standalone: true,
    selector: 'app-admin-ledger',
    imports: [CommonModule],
    providers: [CurrencyPipe, DatePipe],
    templateUrl: './admin-ledger.component.html',
    styleUrls: ['./admin-ledger.component.css']
})
export class AdminLedgerComponent implements OnInit {
    transactions: any[] = [];
    loading = false;
    error = '';

    // Pagination
    currentPage = 0;
    pageSize = 15;
    totalPages = 0;
    totalElements = 0;

    constructor(private adminService: AdminService) { }

    ngOnInit(): void {
        this.loadTransactions();
    }

    loadTransactions(page: number = 0) {
        this.loading = true;
        this.error = '';
        this.currentPage = page;

        this.adminService.getAllTransactions(this.currentPage, this.pageSize).subscribe({
            next: (res) => {
                if (res.success && res.data) {
                    this.transactions = res.data.content;
                    this.totalPages = res.data.totalPages;
                    this.totalElements = res.data.totalElements;
                } else {
                    this.error = res.message || 'Failed to load transactions';
                }
                this.loading = false;
            },
            error: (err) => {
                this.error = err.error?.message || 'Error communicating with server';
                this.loading = false;
            }
        });
    }

    nextPage() {
        if (this.currentPage < this.totalPages - 1) {
            this.loadTransactions(this.currentPage + 1);
        }
    }

    prevPage() {
        if (this.currentPage > 0) {
            this.loadTransactions(this.currentPage - 1);
        }
    }

    getTypeBadgeClass(type: string): string {
        switch (type) {
            case 'TRANSFER': return 'badge bg-primary';
            case 'ADD_FUNDS': return 'badge bg-success';
            case 'WITHDRAWAL': return 'badge bg-warning text-dark';
            case 'PAYMENT': return 'badge bg-info text-dark';
            default: return 'badge bg-secondary';
        }
    }

    getStatusBadgeClass(status: string): string {
        switch (status) {
            case 'COMPLETED': return 'text-success';
            case 'PENDING': return 'text-warning';
            case 'FAILED': return 'text-danger';
            default: return 'text-muted';
        }
    }
}
