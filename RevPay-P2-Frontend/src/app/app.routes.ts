import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login';
import { RegisterComponent } from './auth/register/register';
import { ForgotPasswordComponent } from './auth/forgot-password/forgot-password.component';
import { DashboardComponent } from './wallet/dashboard/dashboard';
import { SendMoneyComponent } from './wallet/send-money/send-money';
import { ProfileComponent } from './auth/profile/profile';
import { CardsComponent } from './wallet/cards/cards';
import { RequestsComponent } from './wallet/requests/requests';
import { TransactionsComponent } from './wallet/transactions/transactions';
import { authGuard } from './core/auth.guard';
import { adminGuard } from './core/admin.guard';
import { AdminLayoutComponent } from './admin/admin-layout/admin-layout.component';
import { AdminDashboardComponent } from './admin/admin-dashboard/admin-dashboard.component';
import { AdminUsersComponent } from './admin/admin-users/admin-users.component';
import { AdminBusinessesComponent } from './admin/admin-businesses/admin-businesses.component';
import { AdminLedgerComponent } from './admin/admin-ledger/admin-ledger.component';
import { AdminLoansComponent } from './admin/admin-loans/admin-loans.component';
import { businessGuard } from './auth/guards/business.guard';
import { BusinessLayoutComponent } from './business/business-layout/business-layout.component';
import { BusinessDashboardComponent } from './business/business-dashboard/business-dashboard.component';
import { BusinessInvoicesComponent } from './business/business-invoices/business-invoices.component';
import { BusinessLoansComponent } from './business/business-loans/business-loans.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'send-money', component: SendMoneyComponent, canActivate: [authGuard] },
  { path: 'cards', component: CardsComponent, canActivate: [authGuard] },
  { path: 'requests', component: RequestsComponent, canActivate: [authGuard] },
  { path: 'transactions', component: TransactionsComponent, canActivate: [authGuard] },
  { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
  {
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [authGuard, adminGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: AdminDashboardComponent },
      { path: 'users', component: AdminUsersComponent },
      { path: 'businesses', component: AdminBusinessesComponent },
      { path: 'ledger', component: AdminLedgerComponent },
      { path: 'loans', component: AdminLoansComponent }
    ]
  },
  {
    path: 'business',
    component: BusinessLayoutComponent,
    canActivate: [authGuard, businessGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: BusinessDashboardComponent },
      { path: 'invoices', component: BusinessInvoicesComponent },
      { path: 'loans', component: BusinessLoansComponent }
    ]
  },
  { path: '**', redirectTo: '/login' }
];
