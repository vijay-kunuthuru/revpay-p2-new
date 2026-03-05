import { Component, ElementRef, ViewChild, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { NotificationService, NotificationDTO } from '../../core/notification.service';

@Component({
  standalone: true,
    selector: 'app-admin-layout',
    imports: [CommonModule, RouterModule],
    templateUrl: './admin-layout.component.html',
    styleUrls: ['./admin-layout.component.css']
})
export class AdminLayoutComponent {
    userEmail: string | null = '';
    showNotifications = false;
    notifications: NotificationDTO[] = [];
    unreadCount = 0;

    @ViewChild('bellIcon') bellIcon?: ElementRef;

    constructor(
        private authService: AuthService,
        private router: Router,
        private notificationService: NotificationService
    ) { }

    ngOnInit() {
        this.userEmail = this.authService.getUserEmail();
        this.loadNotifications();
    }

    loadNotifications() {
        this.notificationService.getNotifications(0, 20).subscribe({
            next: (res) => {
                if (res.success && res.data) {
                    this.notifications = res.data.content;
                    this.unreadCount = this.notifications.filter(n => !n.isRead).length;
                }
            },
            error: (err) => console.error('Failed to load notifications', err)
        });
    }

    markAsRead(notification: NotificationDTO) {
        if (!notification.isRead) {
            this.notificationService.markAsRead(notification.id).subscribe({
                next: () => {
                    notification.isRead = true;
                    this.unreadCount = Math.max(0, this.unreadCount - 1);
                },
                error: (err) => console.error('Failed to mark notification as read', err)
            });
        }
    }

    testNotification() {
        this.notificationService.testNotification().subscribe({
            next: () => this.loadNotifications(),
            error: (err) => console.error('Test notification failed', err)
        });
    }

    toggleNotifications(event: Event) {
        event.stopPropagation();
        this.showNotifications = !this.showNotifications;
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: MouseEvent) {
        if (this.showNotifications && this.bellIcon && !this.bellIcon.nativeElement.contains(event.target)) {
            this.showNotifications = false;
        }
    }

    logout() {
        this.authService.logout();
        this.router.navigate(['/login']);
    }
}
