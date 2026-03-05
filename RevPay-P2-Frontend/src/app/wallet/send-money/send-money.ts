import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { WalletService } from '../wallet.service';

@Component({
  standalone: true,
    selector: 'app-send-money',
    imports: [ReactiveFormsModule],
    templateUrl: './send-money.html',
    styleUrl: './send-money.css'
})
export class SendMoneyComponent {
  sendForm: FormGroup;
  constructor(private fb: FormBuilder, private walletService: WalletService) {
    this.sendForm = this.fb.group({
      receiverIdentifier: ['', Validators.required],
      amount: ['', [Validators.required, Validators.min(1)]],
      description: [''],
      transactionPin: ['', [Validators.required, Validators.pattern(/^[0-9]{4,6}$/)]]
    });
  }
  onSubmit() {
    if (this.sendForm.valid) {
      const payload = {
        ...this.sendForm.value,
        idempotencyKey: crypto.randomUUID()
      };
      this.walletService.sendMoney(payload).subscribe({
        next: () => { alert('Money sent successfully'); this.sendForm.reset(); },
        error: (err) => alert('Failed to send money: ' + (err.error?.message || 'Unknown error'))
      });
    }
  }
}
