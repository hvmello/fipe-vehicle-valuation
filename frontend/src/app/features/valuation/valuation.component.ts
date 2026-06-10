import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { startWith } from 'rxjs';
import { FipeService } from '../../core/fipe.service';
import { Brand, Model, VEHICLE_TYPES, Valuation, VehicleType } from '../../core/models';
import { ValuationResultComponent } from './valuation-result.component';

/** Models shown in the autocomplete at once — brands have hundreds, so we render a light slice. */
const MODEL_RESULT_LIMIT = 50;

/**
 * The selection screen (UI-1..6): dependent Tipo → Marca → Modelo. Type and brand are dropdowns; the
 * model is a **type-ahead autocomplete** because a brand can have ~500 models — the user filters by
 * typing and we render only a small slice, so selection is fast. State is held in signals.
 */
@Component({
  selector: 'app-valuation',
  standalone: true,
  imports: [
    MatCardModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatAutocompleteModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatProgressBarModule,
    ReactiveFormsModule, ValuationResultComponent,
  ],
  templateUrl: './valuation.component.html',
  styleUrl: './valuation.component.scss',
})
export class ValuationComponent {
  private readonly fipe = inject(FipeService);

  readonly vehicleTypes = VEHICLE_TYPES;
  /** Placeholder rows for the loading skeleton (UI-2). */
  readonly skeletonRows = [0, 1, 2, 3];
  readonly modelResultLimit = MODEL_RESULT_LIMIT;

  readonly selectedType = signal<VehicleType | null>(null);
  readonly selectedBrand = signal<string | null>(null);
  readonly selectedModel = signal<string | null>(null);

  readonly brands = signal<Brand[]>([]);
  readonly models = signal<Model[]>([]);
  readonly result = signal<Valuation | null>(null);

  readonly loadingBrands = signal(false);
  readonly loadingModels = signal(false);
  readonly loadingResult = signal(false);
  /** A real failure the user can retry. */
  readonly error = signal<string | null>(null);
  /** An informational "nothing found" message — not an error. */
  readonly notice = signal<string | null>(null);

  /** Search box for the model autocomplete; holds the typed text or the chosen {@link Model}. */
  readonly modelCtrl = new FormControl<string | Model>({ value: '', disabled: true });
  private readonly modelQuery = toSignal(this.modelCtrl.valueChanges.pipe(startWith('')),
    { initialValue: '' as string | Model | null });

  /** All models matching the typed text (case-insensitive substring). */
  private readonly matchedModels = computed(() => {
    const raw = this.modelQuery();
    const query = (typeof raw === 'string' ? raw : raw?.name ?? '').toLowerCase().trim();
    const all = this.models();
    return query ? all.filter((m) => m.name.toLowerCase().includes(query)) : all;
  });

  /** The light slice rendered in the panel. */
  readonly filteredModels = computed(() => this.matchedModels().slice(0, MODEL_RESULT_LIMIT));
  /** Total matches (to hint when the list is truncated). */
  readonly modelMatchCount = computed(() => this.matchedModels().length);

  readonly canSubmit = computed(() =>
    !!this.selectedType() && !!this.selectedBrand() && !!this.selectedModel() && !this.loadingResult());

  /** Renders a chosen model as its name in the input (autocomplete display). */
  readonly displayModel = (value: Model | string | null): string =>
    typeof value === 'string' ? value : value?.name ?? '';

  onTypeChange(type: VehicleType): void {
    this.selectedType.set(type);
    this.resetFrom('brand');
    this.loadingBrands.set(true);
    this.fipe.brands(type).subscribe({
      next: (brands) => {
        this.brands.set(brands);
        this.loadingBrands.set(false);
        if (brands.length === 0) {
          this.notice.set('Nenhuma marca encontrada para este tipo de veículo.');
        }
      },
      error: (err) => this.fail('brands', err),
    });
  }

  onBrandChange(brandId: string): void {
    const type = this.selectedType();
    if (!type) {
      return;
    }
    this.selectedBrand.set(brandId);
    this.resetFrom('model');
    this.loadingModels.set(true);
    this.fipe.models(type, brandId).subscribe({
      next: (models) => {
        this.models.set(models);
        this.loadingModels.set(false);
        if (models.length === 0) {
          this.notice.set('Nenhum modelo encontrado para esta marca.');
        } else {
          this.modelCtrl.enable();
        }
      },
      error: (err) => this.fail('models', err),
    });
  }

  /** A confirmed pick from the autocomplete. */
  onModelSelected(event: MatAutocompleteSelectedEvent): void {
    this.onModelChange((event.option.value as Model).id);
  }

  onModelChange(modelId: string): void {
    this.selectedModel.set(modelId);
    this.result.set(null);
  }

  /** Typing (without picking an option yet) clears any prior selection so the form stays honest. */
  onModelTyping(): void {
    if (this.selectedModel() !== null) {
      this.selectedModel.set(null);
      this.result.set(null);
    }
  }

  consultar(): void {
    const type = this.selectedType();
    const brand = this.selectedBrand();
    const model = this.selectedModel();
    if (!type || !brand || !model) {
      return;
    }
    this.loadingResult.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.result.set(null);
    this.fipe.valuation(type, brand, model).subscribe({
      next: (valuation) => { this.result.set(valuation); this.loadingResult.set(false); },
      error: (err) => this.fail('valuation', err),
    });
  }

  /** Clears selections/data downstream of the given level. */
  private resetFrom(level: 'brand' | 'model'): void {
    this.error.set(null);
    this.notice.set(null);
    this.result.set(null);
    if (level === 'brand') {
      this.selectedBrand.set(null);
      this.brands.set([]);
    }
    this.selectedModel.set(null);
    this.models.set([]);
    this.modelCtrl.reset({ value: '', disabled: true });
  }

  private fail(stage: 'brands' | 'models' | 'valuation', err?: unknown): void {
    this.loadingBrands.set(false);
    this.loadingModels.set(false);
    this.loadingResult.set(false);

    // A 404 means "nothing found", not a failure — show it as an informational notice.
    if (err instanceof HttpErrorResponse && err.status === 404) {
      const notFound = {
        brands: 'Nenhuma marca encontrada para este tipo de veículo.',
        models: 'Nenhum modelo encontrado para esta marca.',
        valuation: 'Nenhuma valorização encontrada para este modelo.',
      };
      this.notice.set(notFound[stage]);
      return;
    }

    const messages = {
      brands: 'Não foi possível carregar as marcas. Tente novamente.',
      models: 'Não foi possível carregar os modelos. Tente novamente.',
      valuation: 'Não foi possível calcular a valorização. Tente novamente.',
    };
    this.error.set(messages[stage]);
  }
}
