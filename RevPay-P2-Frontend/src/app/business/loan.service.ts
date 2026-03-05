import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../shared/models/models';

export interface LoanDTO {
    loanId: number;
    userId: number;
    amount: number;
    interestRate: number;
    tenureMonths: number;
    emiAmount: number;
    remainingAmount: number;
    purpose: string;
    status: string;
    startDate: string;
    endDate: string;
}

export interface LoanApplyDTO {
    amount: number;
    tenureMonths: number;
    purpose: string;
    loanType: string;
    idempotencyKey: string;
    currency?: string;
}

export interface InstallmentDTO {
    installmentId: number;
    loanId: number;
    installmentNumber: number;
    amount: number;
    dueDate: string;
    status: string;
}

export interface LoanAnalyticsDTO {
    totalOutstanding: number;
    totalPaid: number;
    totalPending: number;
}

@Injectable({
    providedIn: 'root'
})
export class LoanService {
    private apiUrl = 'http://localhost:8080/api/v1/loans';

    constructor(private http: HttpClient) { }

    applyForLoan(request: LoanApplyDTO): Observable<ApiResponse<any>> {
        return this.http.post<ApiResponse<any>>(`${this.apiUrl}/apply`, request);
    }

    getMyLoans(page: number = 0, size: number = 10): Observable<ApiResponse<any>> {
        return this.http.get<ApiResponse<any>>(`${this.apiUrl}/my?page=${page}&size=${size}`);
    }

    getEmiSchedule(loanId: number, page: number = 0, size: number = 10): Observable<ApiResponse<any>> {
        return this.http.get<ApiResponse<any>>(`${this.apiUrl}/emi/${loanId}?page=${page}&size=${size}`);
    }

    getAnalytics(): Observable<ApiResponse<LoanAnalyticsDTO>> {
        return this.http.get<ApiResponse<LoanAnalyticsDTO>>(`${this.apiUrl}/analytics`);
    }

    repayLoan(loanId: number, amount: number, transactionPin: string, isFullForeclosure: boolean = false): Observable<ApiResponse<string>> {
        if (isFullForeclosure) {
            return this.http.post<ApiResponse<string>>(`${this.apiUrl}/preclose/${loanId}`, {});
        }

        const payload = {
            loanId,
            amount,
            transactionPin,
            isFullForeclosure,
            idempotencyKey: Date.now().toString()
        };
        return this.http.post<ApiResponse<string>>(`${this.apiUrl}/repay`, payload);
    }
}
