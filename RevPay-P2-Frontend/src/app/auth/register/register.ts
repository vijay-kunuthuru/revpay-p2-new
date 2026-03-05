import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  standalone: true,
    selector: 'app-register',
    imports: [ReactiveFormsModule],
    templateUrl: './register.html',
    styleUrl: './register.css'
})
export class RegisterComponent {
  registerForm: FormGroup;

  constructor(private fb: FormBuilder, private authService: AuthService, private router: Router) {
    this.registerForm = this.fb.group({
      fullName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phoneNumber: ['', [Validators.required, Validators.pattern(/^\+?[0-9]{10,15}$/)]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      transactionPin: ['', [Validators.required, Validators.pattern(/^[0-9]{4,6}$/)]],
      role: ['PERSONAL', Validators.required],
      securityQuestion: ['', Validators.required],
      securityAnswer: ['', Validators.required],
      businessName: [''],
      businessType: [''],
      taxId: [''],
      address: ['']
    });
  }

  onSubmit() {
    if (this.registerForm.valid) {
      const payload = { ...this.registerForm.value };
      if (payload.role !== 'BUSINESS') {
        delete payload.businessName;
        delete payload.businessType;
        delete payload.taxId;
        delete payload.address;
      }

      this.authService.register(payload).subscribe({
        next: () => {
          if (payload.role === 'BUSINESS') {
            alert('Business registration successful! Your profile is pending Admin verification. You can log in, but features may be restricted until approved.');
          } else {
            alert('Registration successful! Please login.');
          }
          this.router.navigate(['/login']);
        },
        error: (err) => {
          console.error(err);
          alert('Registration failed: ' + (err.error?.message || 'Unknown error'));
        }
      });
    }
  }
}
