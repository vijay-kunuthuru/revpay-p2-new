import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth.service';

@Component({
  standalone: true,
    selector: 'app-login',
    imports: [ReactiveFormsModule, RouterLink],
    templateUrl: './login.html',
    styleUrl: './login.css'
})
export class LoginComponent {
  loginForm: FormGroup;
  showPassword = false;

  constructor(private fb: FormBuilder, private authService: AuthService, private router: Router) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });
  }

  togglePassword() {
    this.showPassword = !this.showPassword;
  }

  onSubmit() {
    if (this.loginForm.valid) {
      this.authService.login(this.loginForm.value).subscribe({
        next: (res) => {
          if (res.data && res.data.token) {
            localStorage.setItem('token', res.data.token);
            if (res.data.roles && res.data.roles.length > 0) {
              localStorage.setItem('role', res.data.roles[0]);
            } else if (res.data.role) {
              localStorage.setItem('role', res.data.role);
            }
            if (res.data.userId) localStorage.setItem('userId', res.data.userId.toString());

            this.authService.authStatusChanged.next(true);

            const userRole = localStorage.getItem('role') || '';
            if (userRole.includes('ADMIN')) {
              this.router.navigate(['/admin/dashboard']);
            } else if (userRole.includes('BUSINESS')) {
              this.router.navigate(['/business/dashboard']);
            } else {
              this.router.navigate(['/dashboard']);
            }
          }
        },
        error: (err) => alert('Login failed: ' + (err.error?.message || 'Unknown error'))
      });
    }
  }
}
