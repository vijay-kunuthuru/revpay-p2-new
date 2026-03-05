import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../shared/constants';

export interface NotificationDTO {
    id: number;
    message: string;
    type: string;
    isRead: boolean;
    createdAt: string;
}

export interface PaginatedResponse<T> {
    data: {
        content: T[];
        totalElements: number;
        totalPages: number;
        number: number;
        size: number;
    };
    message: string;
    success: boolean;
}

@Injectable({
    providedIn: 'root'
})
export class NotificationService {
    private apiUrl = `${environment.apiUrl}/notifications`;

    constructor(private http: HttpClient) { }

    getNotifications(page: number = 0, size: number = 20): Observable<PaginatedResponse<NotificationDTO>> {
        return this.http.get<PaginatedResponse<NotificationDTO>>(`${this.apiUrl}?page=${page}&size=${size}`);
    }

    markAsRead(id: number): Observable<any> {
        return this.http.put(`${this.apiUrl}/${id}/read`, {});
    }

    testNotification(): Observable<any> {
        return this.http.post(`${this.apiUrl}/test`, {});
    }
}
