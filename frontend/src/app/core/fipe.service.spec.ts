import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FipeService } from './fipe.service';
import { Valuation } from './models';

describe('FipeService', () => {
  let service: FipeService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FipeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs brands for a vehicle type', () => {
    service.brands('cars').subscribe((brands) => expect(brands).toHaveSize(1));
    const req = httpMock.expectOne('/api/v1/cars/brands');
    expect(req.request.method).toBe('GET');
    req.flush([{ vehicleType: 'cars', id: '21', name: 'Fiat' }]);
  });

  it('GETs models for a brand', () => {
    service.models('cars', '21').subscribe();
    const req = httpMock.expectOne('/api/v1/cars/brands/21/models');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('GETs the valuation for a model', () => {
    const payload: Valuation = {
      vehicleType: 'cars', brand: 'Fiat', model: '147 C/ CL', fipeCode: '001124-0',
      referenceMonth: 'junho de 2026', currency: 'BRL', years: [],
    };
    service.valuation('cars', '21', '437').subscribe((v) => expect(v.currency).toBe('BRL'));
    const req = httpMock.expectOne('/api/v1/cars/brands/21/models/437/valuation');
    expect(req.request.method).toBe('GET');
    req.flush(payload);
  });
});
