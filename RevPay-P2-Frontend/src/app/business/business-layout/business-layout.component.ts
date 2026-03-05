import { Component } from '@angular/core';
import { RouterLink, RouterOutlet, Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';

@Component({
  standalone: true,
    selector: 'app-business-layout',
    imports: [RouterLink, RouterOutlet],
    templateUrl: './business-layout.component.html',
    styleUrl: './business-layout.component.css'
})
export class BusinessLayoutComponent {
  constructor(private authService: AuthService, private router: Router) { }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
