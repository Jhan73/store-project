import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../api/api-config';
import { ReasonsApi } from './reasons-api';
import { reasonLabel, requiresNote } from './reason-rules';

describe('ReasonsApi', () => {
  it('reads the active reasons of a type', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    let result: unknown;

    TestBed.inject(ReasonsApi)
      .active('VOID')
      .subscribe((reasons) => (result = reasons));

    const request = http.expectOne('http://api.test/api/v1/reasons?type=VOID');
    expect(request.request.method).toBe('GET');
    request.flush([{ type: 'VOID', code: 'Error' }]);
    expect(result).toEqual([{ type: 'VOID', code: 'Error' }]);
    http.verify();
  });
});

describe('requiresNote', () => {
  it('asks for a note only for the reason "Other", in any letter case', () => {
    expect(requiresNote('Other')).toBe(true);
    expect(requiresNote('OTHER')).toBe(true);
    expect(requiresNote(' other ')).toBe(true);
    expect(requiresNote('Wrong order')).toBe(false);
    expect(requiresNote(null)).toBe(false);
  });
});

describe('reasonLabel', () => {
  it('words the reason "Other" in Spanish and keeps any other code as the admin wrote it', () => {
    expect(reasonLabel('Other')).toBe('Otro');
    expect(reasonLabel('other')).toBe('Otro');
    expect(reasonLabel('Pedido equivocado')).toBe('Pedido equivocado');
  });
});
