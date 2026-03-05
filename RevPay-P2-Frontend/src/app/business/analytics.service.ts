import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../shared/models/models';

export interface BusinessSummaryDTO {
    totalReceived: number;
    totalSent: number;
    pendingAmount: number;
    totalTransactionCount: number;
    currency: string;
}

@Injectable({
    providedIn: 'root'
})
export class BusinessAnalyticsService {
    private apiUrl = 'http://localhost:8080/api/v1/analytics/business';

    constructor(private http: HttpClient) { }

    getSummary(businessId: number): Observable<ApiResponse<BusinessSummaryDTO>> {
        return this.http.get<ApiResponse<BusinessSummaryDTO>>(`${this.apiUrl}/${businessId}/summary`);
    }
}
