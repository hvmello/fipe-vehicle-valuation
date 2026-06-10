import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Brand, Model, Valuation, VehicleType } from './models';

/**
 * Typed gateway to our backend API (/api/v1). The only place that talks HTTP — components consume
 * these observables (UI-1). In dev, `/api` is proxied to http://localhost:8080 (proxy.conf.json).
 */
@Injectable({ providedIn: 'root' })
export class FipeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1';

  brands(type: VehicleType): Observable<Brand[]> {
    return this.http.get<Brand[]>(`${this.baseUrl}/${type}/brands`);
  }

  models(type: VehicleType, brandId: string): Observable<Model[]> {
    return this.http.get<Model[]>(`${this.baseUrl}/${type}/brands/${brandId}/models`);
  }

  valuation(type: VehicleType, brandId: string, modelId: string): Observable<Valuation> {
    return this.http.get<Valuation>(
      `${this.baseUrl}/${type}/brands/${brandId}/models/${modelId}/valuation`,
    );
  }
}
