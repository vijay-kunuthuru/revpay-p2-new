import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService } from '../admin.service';

@Component({
  standalone: true,
    selector: 'app-admin-users',
    imports: [CommonModule],
    templateUrl: './admin-users.component.html',
    styleUrls: ['./admin-users.component.css']
})
export class AdminUsersComponent implements OnInit {
    users: any[] = [];
    loading = false;
    error = '';

    // Pagination
    currentPage = 0;
    pageSize = 10;
    totalPages = 0;
    totalElements = 0;

    constructor(private adminService: AdminService) { }

    ngOnInit(): void {
        this.loadUsers();
    }

    loadUsers(page: number = 0) {
        this.loading = true;
        this.error = '';
        this.currentPage = page;

        this.adminService.getAllUsers(this.currentPage, this.pageSize).subscribe({
            next: (res) => {
                if (res.success && res.data) {
                    this.users = res.data.content;
                    this.totalPages = res.data.totalPages;
                    this.totalElements = res.data.totalElements;
                } else {
                    this.error = res.message || 'Failed to load users';
                }
                this.loading = false;
            },
            error: (err) => {
                this.error = err.error?.message || 'Error communicating with server';
                this.loading = false;
            }
        });
    }

    toggleUserStatus(user: any) {
        if (user.role === 'ADMIN') {
            alert("Cannot change status of ADMIN users.");
            return;
        }

        const newStatus = !user.active; // If currently active, we want to deactivate
        const action = newStatus ? 'activate' : 'deactivate';

        if (confirm(`Are you sure you want to ${action} user ${user.email}?`)) {
            this.adminService.updateUserStatus(user.userId, newStatus).subscribe({
                next: (res) => {
                    if (res.success) {
                        user.active = newStatus; // Update UI locally
                    } else {
                        alert('Failed to update status: ' + res.message);
                    }
                },
                error: (err) => {
                    alert('Error updating status: ' + (err.error?.message || 'Unknown error'));
                }
            });
        }
    }

    getRoleBadgeClass(role: string): string {
        switch (role) {
            case 'ADMIN': return 'badge bg-danger';
            case 'BUSINESS': return 'badge bg-info text-dark';
            case 'PERSONAL': return 'badge bg-primary';
            default: return 'badge bg-secondary';
        }
    }

    nextPage() {
        if (this.currentPage < this.totalPages - 1) {
            this.loadUsers(this.currentPage + 1);
        }
    }

    prevPage() {
        if (this.currentPage > 0) {
            this.loadUsers(this.currentPage - 1);
        }
    }
}
