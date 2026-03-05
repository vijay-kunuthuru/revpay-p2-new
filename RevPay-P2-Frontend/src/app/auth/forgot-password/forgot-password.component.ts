import { Component } from '@angular/core';

import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  standalone: true,
    selector: 'app-forgot-password',
    imports: [ReactiveFormsModule, RouterLink],
    templateUrl: './forgot-password.component.html',
    styleUrl: './forgot-password.component.css'
})
export class ForgotPasswordComponent {
  step = 1;
  loading = false;
  securityQuestion = '';
  showPassword = false;

  emailForm: FormGroup;
  resetForm: FormGroup;

  constructor(private fb: FormBuilder, private authService: AuthService, private router: Router) {
    this.emailForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]]
    });

    this.resetForm = this.fb.group({
      securityAnswer: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  getSecurityQuestion() {
    if (this.emailForm.valid) {
      this.loading = true;
      const email = this.emailForm.value.email;
      this.authService.forgotPassword(email).subscribe({
        next: (res) => {
          this.securityQuestion = res.data;
          this.step = 2;
          this.loading = false;
        },
        error: (err) => {
          alert('Error: ' + (err.error?.message || 'Email not found.'));
          this.loading = false;
        }
      });
    }
  }

  resetPassword() {
    if (this.resetForm.valid) {
      this.loading = true;
      const payload = {
        email: this.emailForm.value.email,
        securityAnswer: this.resetForm.value.securityAnswer,
        newPassword: this.resetForm.value.newPassword
      };

      this.authService.resetPassword(payload).subscribe({
        next: () => {
          alert('Password reset successfully! You can now log in.');
          this.router.navigate(['/login']);
        },
        error: (err) => {
          alert('Failed to reset password: ' + (err.error?.message || 'Incorrect answer.'));
          this.loading = false;
        }
      });
    }
  }

  togglePassword() {
    this.showPassword = !this.showPassword;
  }
}
