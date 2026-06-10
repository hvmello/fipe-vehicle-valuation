import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { ValuationComponent } from './features/valuation/valuation.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [MatIconModule, ValuationComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {}
