import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../auth/auth.service';

export const adminGuard: CanActivateFn = () => {
    const router = inject(Router);
    const authService = inject(AuthService);

    const token = localStorage.getItem('token');
    if (!token) {
        router.navigate(['/login']);
        return false;
    }

    const role = authService.getUserRole();
    if (role === 'ROLE_ADMIN' || role === 'ADMIN') {
        return true;
    }

    // Not an admin, redirect to normal dashboard
    router.navigate(['/dashboard']);
    return false;
};
