import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../auth.service';

export const businessGuard: CanActivateFn = () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const role = authService.getUserRole();
    if (authService.isLoggedIn() && (role === 'BUSINESS' || role === 'ROLE_BUSINESS' || role === 'ADMIN')) {
        return true;
    }

    router.navigate(['/dashboard']);
    return false;
};
