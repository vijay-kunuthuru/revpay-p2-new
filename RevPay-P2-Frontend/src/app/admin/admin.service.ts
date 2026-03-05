import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../shared/constants';
import { ApiResponse } from '../shared/models/models';

export interface SystemAnalytics {
    totalUsers: number;
    activeBusinesses: number;
    totalTransactions: number;
    totalVolume: number;
    adminWalletBalance?: number;
}

@Injectable({
    providedIn: 'root'
})
export class AdminService {
    private apiUrl = `${environment.apiUrl}/admin`;

    constructor(private http: HttpClient) { }

    getSystemAnalytics(): Observable<ApiResponse<SystemAnalytics>> {
        return this.http.get<ApiResponse<SystemAnalytics>>(`${this.apiUrl}/analytics`);
    }

    getAllUsers(page: number, size: number): Observable<ApiResponse<any>> {
        return this.http.get<ApiResponse<any>>(`${this.apiUrl}/users?page=${page}&size=${size}`);
    }

    updateUserStatus(userId: number, isActive: boolean): Observable<ApiResponse<any>> {
        return this.http.put<ApiResponse<any>>(`${this.apiUrl}/users/${userId}/status`, { isActive });
    }

    getAllBusinesses(page: number, size: number): Observable<ApiResponse<any>> {
        return this.http.get<ApiResponse<any>>(`${this.apiUrl}/businesses?page=${page}&size=${size}`);
    }

    verifyBusiness(profileId: number): Observable<ApiResponse<any>> {
        return this.http.post<ApiResponse<any>>(`${this.apiUrl}/businesses/${profileId}/verify`, {});
    }

    suspendBusiness(profileId: number): Observable<ApiResponse<any>> {
        return this.http.put<ApiResponse<any>>(`${this.apiUrl}/businesses/${profileId}/suspend`, {});
    }

    getAllTransactions(page: number, size: number): Observable<ApiResponse<any>> {
        return this.http.get<ApiResponse<any>>(`${this.apiUrl}/transactions?page=${page}&size=${size}`);
    }

    getAllLoans(page: number, size: number): Observable<ApiResponse<any>> {
        return this.http.get<ApiResponse<any>>(`${this.apiUrl}/loans/all?page=${page}&size=${size}`);
    }

    approveLoan(payload: { loanId: number, approved: boolean, approvedInterestRate?: number, rejectionReason?: string }): Observable<ApiResponse<any>> {
        return this.http.post<ApiResponse<any>>(`${this.apiUrl}/loans/approve`, payload);
    }
}
