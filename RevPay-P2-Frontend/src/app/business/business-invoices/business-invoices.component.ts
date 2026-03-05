import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { InvoiceService, InvoiceDTO } from '../invoice.service';
import { BusinessProfileService } from '../business-profile.service';

@Component({
  standalone: true,
    selector: 'app-business-invoices',
    imports: [CommonModule, ReactiveFormsModule],
    templateUrl: './business-invoices.component.html',
    styleUrl: './business-invoices.component.css'
})
export class BusinessInvoicesComponent implements OnInit {
  isVerified = false;
  profileId: number = 0;

  showCreateForm = false;
  invoiceForm: FormGroup;

  invoices: InvoiceDTO[] = [];
  currentPage = 0;
  totalPages = 0;

  constructor(
    private invoiceService: InvoiceService,
    private profileService: BusinessProfileService,
    private fb: FormBuilder
  ) {
    this.invoiceForm = this.fb.group({
      customerName: ['', Validators.required],
      customerEmail: ['', [Validators.required, Validators.email]],
      totalAmount: ['', [Validators.required, Validators.min(0.01)]],
      dueDate: ['', Validators.required]
    });
  }

  ngOnInit() {
    this.profileService.getMyProfile().subscribe({
      next: (res) => {
        this.isVerified = res.data.isVerified;
        this.profileId = res.data.profileId;
        if (this.isVerified) {
          this.loadInvoices(0);
        }
      },
      error: (err) => console.error("Failed to load business profile", err)
    });
  }

  loadInvoices(page: number) {
    this.invoiceService.getInvoices(this.profileId, page, 10).subscribe({
      next: (res) => {
        // Assume API returns a paginated structure in data field
        this.invoices = res.data.content;
        this.currentPage = res.data.number;
        this.totalPages = res.data.totalPages;
      },
      error: (err) => console.error("Failed to load invoices", err)
    });
  }

  createInvoice() {
    if (this.invoiceForm.valid) {
      this.invoiceService.createInvoice(this.invoiceForm.value).subscribe({
        next: (res) => {
          this.showCreateForm = false;
          this.invoiceForm.reset();
          this.loadInvoices(this.currentPage); // Refresh current page
          alert("Invoice created successfully!");
        },
        error: (err) => {
          console.error(err);
          alert("Failed to create invoice: " + (err.error?.message || "Unknown error"));
        }
      });
    }
  }

  sendInvoice(id: number) {
    if (confirm("Are you sure you want to send this invoice to the customer?")) {
      this.invoiceService.sendInvoice(id).subscribe({
        next: () => {
          this.loadInvoices(this.currentPage);
          alert("Invoice sent successfully!");
        },
        error: (err) => alert("Failed to send invoice.")
      });
    }
  }

  markPaid(id: number) {
    if (confirm("Mark this invoice as manually paid?")) {
      this.invoiceService.markPaid(id).subscribe({
        next: () => {
          this.loadInvoices(this.currentPage);
          alert("Invoice marked as paid!");
        },
        error: (err) => alert("Failed to mark invoice as paid.")
      });
    }
  }
}
