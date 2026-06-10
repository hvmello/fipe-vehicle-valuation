import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Valuation } from '../../core/models';
import { ValuationResultComponent } from './valuation-result.component';

// The template's currency/number pipes use explicit 'pt-BR'; register it for the test env
// (the real app registers it in app.config.ts).
registerLocaleData(localePt);

const sample: Valuation = {
  vehicleType: 'cars', brand: 'Fiat', model: '147 C/ CL', fipeCode: '001124-0',
  referenceMonth: 'junho de 2026', currency: 'BRL',
  years: [
    { year: 2013, label: '2013', fuel: 'Gasolina', price: 25000, change: 2500, changePercent: 11.11, previousYear: 2011, previousLabel: '2011' },
    { year: 2011, label: '2011', fuel: 'Gasolina', price: 22500, change: -2250, changePercent: -9.09, previousYear: 2010, previousLabel: '2010' },
    { year: 2009, label: '2009', fuel: 'Gasolina', price: 18225, change: null, changePercent: null, previousYear: null, previousLabel: null },
  ],
};

describe('ValuationResultComponent', () => {
  let fixture: ComponentFixture<ValuationResultComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ValuationResultComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(ValuationResultComponent);
    fixture.componentRef.setInput('valuation', sample);
    fixture.detectChanges();
  });

  it('UI-3: renders one row per year, newest first', () => {
    const rows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(3);
    expect((rows[0] as HTMLElement).textContent).toContain('2013');
  });

  it('UI-3: positive variation is up, negative is down', () => {
    expect(fixture.nativeElement.querySelector('.up')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.down')).toBeTruthy();
  });

  it('UI-4: oldest year shows the base value, no variation', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('valor base');
  });

  it('UI-6: header shows brand, model, FIPE code and reference month', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Fiat');
    expect(text).toContain('147 C/ CL');
    expect(text).toContain('001124-0');
    expect(text).toContain('junho de 2026');
  });
});
