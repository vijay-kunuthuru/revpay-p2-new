import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../auth.service';


@Component({
  standalone: true,
    selector: 'app-profile',
    imports: [ReactiveFormsModule],
    templateUrl: './profile.html',
    styleUrl: './profile.css'
})
export class ProfileComponent {
  passwordForm: FormGroup;
  pinForm: FormGroup;

  constructor(private fb: FormBuilder, private authService: AuthService) {
    this.passwordForm = this.fb.group({
      oldPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8)]]
    });

    this.pinForm = this.fb.group({
      oldPin: ['', Validators.required],
      newPin: ['', [Validators.required, Validators.pattern(/^[0-9]{4,6}$/)]]
    });
  }

  onUpdatePassword() {
    if (this.passwordForm.valid) {
      this.authService.updatePassword(this.passwordForm.value).subscribe({
        next: () => {
          alert('Password updated successfully!');
          this.passwordForm.reset();
        },
        error: (err) => alert('Failed to update password: ' + (err.error?.message || 'Unknown error'))
      });
    }
  }

  onUpdatePin() {
    if (this.pinForm.valid) {
      this.authService.updatePin(this.pinForm.value).subscribe({
        next: () => {
          alert('Transaction PIN updated successfully!');
          this.pinForm.reset();
        },
        error: (err) => alert('Failed to update PIN: ' + (err.error?.message || 'Unknown error'))
      });
    }
  }
}
