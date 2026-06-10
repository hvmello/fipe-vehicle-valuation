/** Mirrors the backend API contract (specs/001-vehicle-valuation/api-contract.md Part A). */

export type VehicleType = 'cars' | 'motorcycles' | 'trucks';

export interface VehicleTypeOption {
  readonly value: VehicleType;
  readonly label: string;
}

/** Vehicle types offered in the UI (UI-1), labelled in Portuguese. */
export const VEHICLE_TYPES: readonly VehicleTypeOption[] = [
  { value: 'cars', label: 'Carros' },
  { value: 'motorcycles', label: 'Motos' },
  { value: 'trucks', label: 'Caminhões' },
];

export interface Brand {
  readonly vehicleType: string;
  readonly id: string;
  readonly name: string;
}

export interface Model {
  readonly vehicleType: string;
  readonly id: string;
  readonly name: string;
}

export interface YearValuation {
  readonly year: number;
  readonly label: string;
  readonly fuel: string;
  readonly price: number;
  /** null for the oldest entry. */
  readonly change: number | null;
  readonly changePercent: number | null;
  readonly previousYear: number | null;
  readonly previousLabel: string | null;
}

export interface Valuation {
  readonly vehicleType: string;
  readonly brand: string;
  readonly model: string;
  readonly fipeCode: string;
  readonly referenceMonth: string;
  readonly currency: string;
  readonly years: readonly YearValuation[];
}
