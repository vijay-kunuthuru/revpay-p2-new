import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService } from '../admin.service';

@Component({
  standalone: true,
    selector: 'app-admin-businesses',
    imports: [CommonModule],
    templateUrl: './admin-businesses.component.html',
    styleUrls: ['./admin-businesses.component.css']
})
export class AdminBusinessesComponent implements OnInit {
    businesses: any[] = [];
    loading = false;
    error = '';

    // Pagination
    currentPage = 0;
    pageSize = 10;
    totalPages = 0;
    totalElements = 0;

    constructor(private adminService: AdminService) { }

    ngOnInit(): void {
        this.loadBusinesses();
    }

    loadBusinesses(page: number = 0) {
        this.loading = true;
        this.error = '';
        this.currentPage = page;

        this.adminService.getAllBusinesses(this.currentPage, this.pageSize).subscribe({
            next: (res) => {
                if (res.success && res.data) {
                    this.businesses = res.data.content;
                    this.totalPages = res.data.totalPages;
                    this.totalElements = res.data.totalElements;
                } else {
                    this.error = res.message || 'Failed to load businesses';
                }
                this.loading = false;
            },
            error: (err) => {
                this.error = err.error?.message || 'Error communicating with server';
                this.loading = false;
            }
        });
    }

    verifyBusiness(business: any) {
        if (confirm(`Approve business profile for ${business.businessName}?`)) {
            this.adminService.verifyBusiness(business.profileId).subscribe({
                next: (res) => {
                    if (res.success) {
                        business.verified = true;
                        alert('Business verified successfully.');
                    } else {
                        alert('Verification failed: ' + res.message);
                    }
                },
                error: (err) => alert('Error: ' + (err.error?.message || 'Unknown error'))
            });
        }
    }

    suspendBusiness(business: any) {
        if (confirm(`Suspend business profile for ${business.businessName}? This will prevent them from issuing invoices.`)) {
            this.adminService.suspendBusiness(business.profileId).subscribe({
                next: (res) => {
                    if (res.success) {
                        business.verified = false;
                        alert('Business suspended successfully.');
                    } else {
                        alert('Suspension failed: ' + res.message);
                    }
                },
                error: (err) => alert('Error: ' + (err.error?.message || 'Unknown error'))
            });
        }
    }

    nextPage() {
        if (this.currentPage < this.totalPages - 1) {
            this.loadBusinesses(this.currentPage + 1);
        }
    }

    prevPage() {
        if (this.currentPage > 0) {
            this.loadBusinesses(this.currentPage - 1);
        }
    }
}
