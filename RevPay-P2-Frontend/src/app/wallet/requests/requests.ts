import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { WalletService } from '../wallet.service';
import { Transaction } from '../../shared/models/models';

@Component({
  standalone: true,
    selector: 'app-requests',
    imports: [CommonModule, ReactiveFormsModule],
    templateUrl: './requests.html',
    styleUrl: './requests.css'
})
export class RequestsComponent implements OnInit {
  incomingRequests: Transaction[] = [];
  outgoingRequests: Transaction[] = [];
  requestForm: FormGroup;

  constructor(private fb: FormBuilder, private walletService: WalletService) {
    this.requestForm = this.fb.group({
      targetEmail: ['', [Validators.required, Validators.email]],
      amount: ['', [Validators.required, Validators.min(1)]]
    });
  }

  ngOnInit() {
    this.loadIncoming();
    this.loadOutgoing();
  }

  loadIncoming() {
    // Backend's 'outgoing' = money outgoing from me = UI 'incoming request'
    this.walletService.getOutgoingRequests().subscribe(res => {
      if (res.data && res.data.content) this.incomingRequests = res.data.content;
    });
  }

  loadOutgoing() {
    // Backend's 'incoming' = money incoming to me = UI 'outgoing request'
    this.walletService.getIncomingRequests().subscribe(res => {
      if (res.data && res.data.content) this.outgoingRequests = res.data.content;
    });
  }

  onRequestMoney() {
    if (this.requestForm.valid) {
      this.walletService.requestMoney(this.requestForm.value).subscribe({
        next: () => {
          alert('Money request sent!');
          this.requestForm.reset();
          this.loadOutgoing();
        },
        error: (err) => alert('Failed to send request: ' + (err.error?.message || 'Unknown error'))
      });
    }
  }

  acceptRequest(txnId: number, pin: string) {
    if (!pin) { alert('Please enter your transaction PIN to accept'); return; }
    this.walletService.acceptRequest(txnId, pin).subscribe({
      next: () => {
        alert('Request accepted and paid successfully!');
        this.loadIncoming();
      },
      error: (err) => alert('Failed to accept: ' + (err.error?.message || 'Unknown error'))
    });
  }

  declineRequest(txnId: number) {
    this.walletService.declineRequest(txnId).subscribe({
      next: () => {
        alert('Request declined.');
        this.loadIncoming();
      },
      error: (err) => alert('Failed to decline: ' + (err.error?.message || 'Unknown error'))
    });
  }
}
