import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../shared/models/models';

export interface InvoiceDTO {
    id: number;
    businessId: number;
    customerName: string;
    customerEmail: string;
    totalAmount: number;
    dueDate: string;
    status: string;
}

export interface InvoiceCreateRequest {
    customerName: string;
    customerEmail: string;
    totalAmount: number;
    dueDate: string;
}

@Injectable({
    providedIn: 'root'
})
export class InvoiceService {
    private apiUrl = 'http://localhost:8080/api/v1/business/invoices';

    constructor(private http: HttpClient) { }

    createInvoice(request: InvoiceCreateRequest): Observable<ApiResponse<InvoiceDTO>> {
        return this.http.post<ApiResponse<InvoiceDTO>>(this.apiUrl, request);
    }

    getInvoices(profileId: number, page: number = 0, size: number = 10): Observable<ApiResponse<any>> {
        return this.http.get<ApiResponse<any>>(`http://localhost:8080/api/v1/business/${profileId}/invoices?page=${page}&size=${size}`);
    }

    sendInvoice(id: number): Observable<ApiResponse<string>> {
        return this.http.post<ApiResponse<string>>(`${this.apiUrl}/${id}/send`, {});
    }

    markPaid(id: number): Observable<ApiResponse<string>> {
        return this.http.patch<ApiResponse<string>>(`${this.apiUrl}/${id}/pay`, {});
    }
}
