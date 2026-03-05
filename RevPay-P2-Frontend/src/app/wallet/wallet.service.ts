import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../shared/constants';
import { ApiResponse, Transaction, PaymentMethodDTO, CardPayload } from '../shared/models/models';

@Injectable({ providedIn: 'root' })
export class WalletService {
  private baseUrl = `${environment.apiUrl}/wallet`;
  constructor(private http: HttpClient) { }

  // Core Money Movement
  getBalance(): Observable<ApiResponse<number>> {
    return this.http.get<ApiResponse<number>>(`${this.baseUrl}/balance`);
  }

  addFunds(payload: any): Observable<ApiResponse<Transaction>> {
    return this.http.post<ApiResponse<Transaction>>(`${this.baseUrl}/add-funds`, payload);
  }

  sendMoney(payload: any): Observable<ApiResponse<Transaction>> {
    return this.http.post<ApiResponse<Transaction>>(`${this.baseUrl}/send`, payload);
  }

  getTransactions(page = 0, size = 20): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.baseUrl}/transactions?page=${page}&size=${size}`);
  }

  exportTransactionPdf(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/transactions/export/pdf`, { responseType: 'blob' });
  }

  // Cards
  getCards(page = 0, size = 10): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.baseUrl}/cards?page=${page}&size=${size}`);
  }

  addCard(payload: CardPayload): Observable<ApiResponse<PaymentMethodDTO>> {
    return this.http.post<ApiResponse<PaymentMethodDTO>>(`${this.baseUrl}/cards`, payload);
  }

  deleteCard(cardId: number): Observable<ApiResponse<string>> {
    return this.http.delete<ApiResponse<string>>(`${this.baseUrl}/cards/${cardId}`);
  }

  setDefaultCard(cardId: number): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.baseUrl}/cards/default/${cardId}`, {});
  }

  // Requests
  requestMoney(payload: any): Observable<ApiResponse<Transaction>> {
    return this.http.post<ApiResponse<Transaction>>(`${this.baseUrl}/request`, payload);
  }

  getIncomingRequests(page = 0, size = 10): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.baseUrl}/requests/incoming?page=${page}&size=${size}`);
  }

  getOutgoingRequests(page = 0, size = 10): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.baseUrl}/requests/outgoing?page=${page}&size=${size}`);
  }

  acceptRequest(txnId: number, pin: string): Observable<ApiResponse<Transaction>> {
    return this.http.post<ApiResponse<Transaction>>(`${this.baseUrl}/request/accept/${txnId}`, { pin });
  }

  declineRequest(txnId: number): Observable<ApiResponse<Transaction>> {
    return this.http.post<ApiResponse<Transaction>>(`${this.baseUrl}/request/decline/${txnId}`, {});
  }

  // Filter
  filterTransactions(paramsObj: any): Observable<ApiResponse<any>> {
    let params = new HttpParams();
    Object.keys(paramsObj).forEach(key => {
      if (paramsObj[key] !== null && paramsObj[key] !== '') {
        // Handle dates - Spring Boot ISO format requires dropping the Z if it's there
        if ((key === 'startDate' || key === 'endDate') && typeof paramsObj[key] === 'string') {
          // Remove trailing Z if present, as the backend expects ISO Date Time without zone for LocalDateTime
          const dateStr = paramsObj[key].endsWith('Z') ? paramsObj[key].slice(0, -1) : paramsObj[key];
          params = params.append(key, dateStr);
        } else {
          params = params.append(key, paramsObj[key]);
        }
      }
    });
    return this.http.get<ApiResponse<any>>(`${this.baseUrl}/transactions/filter`, { params });
  }
}
