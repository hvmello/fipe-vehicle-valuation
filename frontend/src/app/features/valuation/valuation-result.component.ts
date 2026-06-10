import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { Valuation } from '../../core/models';

/**
 * Renders a valuation as a Material table, newest year first (UI-3,4,6). Positive variation is
 * green with an up arrow, negative red with a down arrow; the oldest year shows the base value.
 * Percent is displayed rounded to a whole number (the API keeps 2 decimals).
 */
@Component({
  selector: 'app-valuation-result',
  standalone: true,
  imports: [MatCardModule, MatTableModule, MatIconModule, CurrencyPipe, DecimalPipe],
  templateUrl: './valuation-result.component.html',
  styleUrl: './valuation-result.component.scss',
})
export class ValuationResultComponent {
  readonly valuation = input.required<Valuation>();

  readonly columns = ['label', 'price', 'variation'] as const;
}
