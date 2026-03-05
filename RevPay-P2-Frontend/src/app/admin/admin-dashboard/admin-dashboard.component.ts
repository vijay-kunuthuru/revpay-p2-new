import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService, SystemAnalytics } from '../admin.service';

@Component({
  standalone: true,
    selector: 'app-admin-dashboard',
    imports: [CommonModule],
    templateUrl: './admin-dashboard.component.html',
    styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {
    analytics: SystemAnalytics | null = null;
    loading = true;
    error = '';

    constructor(private adminService: AdminService) { }

    ngOnInit(): void {
        this.loadAnalytics();
    }

    loadAnalytics() {
        this.loading = true;
        this.adminService.getSystemAnalytics().subscribe({
            next: (res) => {
                if (res.success && res.data) {
                    this.analytics = res.data;
                } else {
                    this.error = res.message || 'Failed to load analytics';
                }
                this.loading = false;
            },
            error: (err) => {
                this.error = err.error?.message || 'An error occurred loading analytics';
                this.loading = false;
            }
        });
    }
}
