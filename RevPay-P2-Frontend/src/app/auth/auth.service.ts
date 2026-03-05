import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { environment } from '../shared/constants';
import { LoginRequest, SignupRequest, ApiResponse, JwtResponse, UpdatePasswordRequest } from '../shared/models/models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private baseUrl = `${environment.apiUrl}/auth`;
  public authStatusChanged = new Subject<boolean>();

  constructor(private http: HttpClient) { }

  login(data: LoginRequest): Observable<ApiResponse<JwtResponse>> {
    return this.http.post<ApiResponse<JwtResponse>>(`${this.baseUrl}/login`, data);
  }

  register(data: SignupRequest): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.baseUrl}/register`, data);
  }

  updatePassword(data: UpdatePasswordRequest): Observable<ApiResponse<string>> {
    return this.http.put<ApiResponse<string>>(`${this.baseUrl}/update-password`, data);
  }

  updatePin(data: any): Observable<ApiResponse<string>> {
    return this.http.put<ApiResponse<string>>(`${this.baseUrl}/update-pin`, data);
  }

  forgotPassword(email: string): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.baseUrl}/forgot-password`, { email });
  }

  resetPassword(data: any): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${this.baseUrl}/reset-password`, data);
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    localStorage.removeItem('userId');
    this.authStatusChanged.next(false);
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem('token');
  }

  getUserEmail(): string | null {
    const token = localStorage.getItem('token');
    if (!token) return null;
    try {
      const payloadBase64 = token.split('.')[1];
      const payloadString = atob(payloadBase64);
      const payload = JSON.parse(payloadString);
      return payload.sub || null; // 'sub' typically holds the email in standard JWTs
    } catch (e) {
      return null;
    }
  }

  getUserRole(): string | null {
    return localStorage.getItem('role');
  }

  getUserId(): number | null {
    const userId = localStorage.getItem('userId');
    return userId ? parseInt(userId, 10) : null;
  }
}
