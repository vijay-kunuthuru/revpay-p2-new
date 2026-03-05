import { Component, OnInit } from '@angular/core';

import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { WalletService } from '../wallet.service';
import { PaymentMethodDTO } from '../../shared/models/models';

@Component({
  standalone: true,
    selector: 'app-cards',
    imports: [ReactiveFormsModule],
    templateUrl: './cards.html',
    styleUrl: './cards.css'
})
export class CardsComponent implements OnInit {
  cards: PaymentMethodDTO[] = [];
  cardForm: FormGroup;

  constructor(private fb: FormBuilder, private walletService: WalletService) {
    this.cardForm = this.fb.group({
      cardNumber: ['', [Validators.required, Validators.pattern(/^[0-9]{13,19}$/)]],
      expiryDate: ['', [Validators.required, Validators.pattern(/^(0[1-9]|1[0-2])\/?([0-9]{2})$/)]],
      cvv: ['', [Validators.required, Validators.pattern(/^[0-9]{3,4}$/)]],
      cardType: ['VISA', Validators.required],
      billingAddress: ['']
    });
  }

  ngOnInit() {
    this.loadCards();
  }

  loadCards() {
    this.walletService.getCards().subscribe({
      next: (res) => {
        if (res.data && res.data.content) {
          this.cards = res.data.content;
        }
      },
      error: (err) => console.error('Failed to load cards', err)
    });
  }

  onAddCard() {
    if (this.cardForm.valid) {
      // Ensure cardType is uppercase before sending, though the select already provides it.
      const cardData = { ...this.cardForm.value, cardType: this.cardForm.value.cardType.toUpperCase() };
      this.walletService.addCard(cardData).subscribe({
        next: () => {
          alert('Card added successfully!');
          this.cardForm.reset({ cardType: 'VISA' });
          this.loadCards();
        },
        error: (err) => alert('Failed to add card: ' + (err.error?.message || 'Unknown error'))
      });
    }
  }

  deleteCard(id: number) {
    if (confirm('Are you sure you want to remove this card?')) {
      this.walletService.deleteCard(id).subscribe({
        next: () => {
          this.loadCards();
        },
        error: (err) => alert('Failed to delete card')
      });
    }
  }

  setDefault(id: number) {
    this.walletService.setDefaultCard(id).subscribe({
      next: () => {
        this.loadCards();
      },
      error: (err) => alert('Failed to set default card')
    });
  }
}
