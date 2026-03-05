import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { WalletService } from '../wallet.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  standalone: true,
    selector: 'app-dashboard',
    imports: [CommonModule, RouterLink, ReactiveFormsModule],
    templateUrl: './dashboard.html',
    styleUrl: './dashboard.css'
})
export class DashboardComponent implements OnInit {
  balance: number = 0;
  transactions: any[] = [];
  cards: any[] = [];

  showAddFunds: boolean = false;
  addFundsForm: FormGroup;
  currentUserId: number | null = null;

  constructor(private fb: FormBuilder, private walletService: WalletService, private authService: AuthService) {
    this.currentUserId = this.authService.getUserId();
    this.addFundsForm = this.fb.group({
      amount: ['', [Validators.required, Validators.min(1)]],
      cardId: ['', Validators.required],
      description: ['']
    });
  }

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.walletService.getBalance().subscribe(res => {
      if (res.data) this.balance = res.data;
    });
    this.walletService.getTransactions(0, 15).subscribe(res => {
      if (res.data && res.data.content) {
        this.transactions = res.data.content
          .filter((txn: any) => txn.type !== 'REQUEST')
          .slice(0, 5);
      }
    });
    this.walletService.getCards(0, 50).subscribe(res => {
      if (res.data && res.data.content) this.cards = res.data.content;
    });
  }

  isOutgoing(txn: any): boolean {
    if (txn.senderId && txn.receiverId && txn.senderId !== txn.receiverId) {
      return Number(txn.senderId) === Number(this.currentUserId);
    }
    if (txn.type === 'ADD_FUNDS' || txn.type === 'DEPOSIT') {
      return false;
    }
    if (txn.type === 'WITHDRAWAL') {
      return true;
    }
    if (txn.type === 'LOAN_DISBURSEMENT') {
      const role = this.authService.getUserRole();
      return role === 'ADMIN' || role === 'ROLE_ADMIN';
    }
    if (txn.type === 'LOAN_REPAYMENT') {
      const role = this.authService.getUserRole();
      return role !== 'ADMIN' && role !== 'ROLE_ADMIN';
    }
    return txn.type === 'SEND' || txn.type === 'PAYMENT' || txn.type === 'TRANSFER' || txn.type === 'INVOICE_PAYMENT';
  }

  onAddFunds() {
    if (this.addFundsForm.valid) {
      // Create a payload omitting cardId if the backend AddFunds payload doesn't actually use it,
      // but for UX we enforced them picking it. 
      // If the backend DOES require a source, we pass it. Assuming we just pass it along:
      this.walletService.addFunds(this.addFundsForm.value).subscribe({
        next: () => {
          alert('Funds added successfully!');
          this.addFundsForm.reset({ cardId: '' });
          this.showAddFunds = false;
          this.loadData(); // Refresh balance and txns
        },
        error: (err) => alert('Failed to add funds: ' + (err.error?.message || 'Unknown error'))
      });
    }
  }

  abs(value: number): number {
    return Math.abs(value);
  }

  sanitizeDescription(desc: string): string {
    if (!desc) return 'No description provided';
    if (desc === 'Added via: null' || desc === 'Added via: ' || desc === 'Added via: null ') {
      return 'Added Funds';
    }
    return desc;
  }
}
