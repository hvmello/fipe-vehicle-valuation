import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { NEVER, of, throwError } from 'rxjs';
import { FipeService } from '../../core/fipe.service';
import { ValuationComponent } from './valuation.component';

describe('ValuationComponent', () => {
  let component: ValuationComponent;
  let fipe: jasmine.SpyObj<FipeService>;

  beforeEach(async () => {
    fipe = jasmine.createSpyObj<FipeService>('FipeService', ['brands', 'models', 'valuation']);
    fipe.brands.and.returnValue(of([{ vehicleType: 'cars', id: '21', name: 'Fiat' }]));
    fipe.models.and.returnValue(of([{ vehicleType: 'cars', id: '437', name: '147 C/ CL' }]));

    await TestBed.configureTestingModule({
      imports: [ValuationComponent],
      providers: [provideNoopAnimations(), { provide: FipeService, useValue: fipe }],
    }).compileComponents();

    const fixture = TestBed.createComponent(ValuationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('UI-1: selecting a type loads brands and resets downstream', () => {
    component.onBrandChange('21'); // ignored: no type yet
    component.onTypeChange('cars');
    expect(fipe.brands).toHaveBeenCalledWith('cars');
    expect(component.brands().length).toBe(1);
    expect(component.selectedModel()).toBeNull();
  });

  it('UI-1: model autocomplete filters by typed text', () => {
    component.onTypeChange('cars');
    component.onBrandChange('21'); // loads [{ id: '437', name: '147 C/ CL' }]
    expect(component.filteredModels().length).toBe(1);

    component.modelCtrl.setValue('xyz');
    expect(component.filteredModels().length).toBe(0);

    component.modelCtrl.setValue('147');
    expect(component.filteredModels().map((m) => m.id)).toEqual(['437']);
  });

  it('UI-1: Consultar stays disabled until type + brand + model are chosen', () => {
    expect(component.canSubmit()).toBeFalse();
    component.onTypeChange('cars');
    expect(component.canSubmit()).toBeFalse();
    component.onBrandChange('21');
    expect(component.canSubmit()).toBeFalse();
    component.onModelChange('437');
    expect(component.canSubmit()).toBeTrue();
  });

  it('UI-2: a request in flight shows the loading state and disables the trigger', () => {
    fipe.valuation.and.returnValue(NEVER); // never completes → stays in flight
    component.onTypeChange('cars');
    component.onBrandChange('21');
    component.onModelChange('437');
    expect(component.canSubmit()).toBeTrue();

    component.consultar();

    expect(component.loadingResult()).toBeTrue();   // spinner shown
    expect(component.canSubmit()).toBeFalse();       // Consultar disabled while loading
  });

  it('UI-5: an API error shows a friendly message and leaves the form usable', () => {
    fipe.brands.and.returnValue(throwError(() => new Error('network down')));
    component.onTypeChange('cars');

    // Our friendly message, not the raw exception text.
    expect(component.error()).toBe('Não foi possível carregar as marcas. Tente novamente.');
    expect(component.error()).not.toContain('network down'); // raw error never leaked
    expect(component.loadingBrands()).toBeFalse();            // form not stuck loading
    expect(component.selectedType()).toBe('cars');            // still usable
  });
});
