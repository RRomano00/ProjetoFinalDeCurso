import { TestBed } from '@angular/core/testing';
import { ToastrService } from 'ngx-toastr';
import { LocalityPreferenceService } from './locality-preference.service';
import { GeocodingService } from './geocoding.service';
import { UserReadService } from '../user/user-read.service';

describe('LocalityPreferenceService.mapCenter()', () => {
  let sut: LocalityPreferenceService;
  let geocoding: jasmine.SpyObj<GeocodingService>;
  let userRead: jasmine.SpyObj<UserReadService>;

  const ITAJUBA = { lat: -22.42, lng: -45.45 };

  beforeEach(() => {
    localStorage.removeItem('locality.choice');
    geocoding = jasmine.createSpyObj('GeocodingService', ['geocode', 'reverseGeocode']);
    userRead  = jasmine.createSpyObj('UserReadService', ['findById']);
    TestBed.configureTestingModule({
      providers: [
        LocalityPreferenceService,
        { provide: GeocodingService, useValue: geocoding },
        { provide: UserReadService,  useValue: userRead },
        { provide: ToastrService,    useValue: jasmine.createSpyObj('ToastrService', ['warning', 'info']) },
      ]
    });
    sut = TestBed.inject(LocalityPreferenceService);
  });

  afterEach(() => localStorage.removeItem('locality.choice'));

  it('nunca pergunta a localização ao aparelho', async () => {
    const gps = spyOn(sut, 'position');
    localStorage.setItem('id', '7');
    userRead.findById.and.resolveTo({ city: 'Itajubá', state: 'MG' } as any);
    geocoding.geocode.and.resolveTo(ITAJUBA);

    await sut.mapCenter();

    expect(gps).not.toHaveBeenCalled();
    localStorage.removeItem('id');
  });

  it('sem escolha, abre no município do cadastro', async () => {
    localStorage.setItem('id', '7');
    userRead.findById.and.resolveTo({ city: 'Itajubá', state: 'MG' } as any);
    geocoding.geocode.and.resolveTo(ITAJUBA);

    expect(await sut.mapCenter()).toEqual({ ...ITAJUBA, zoom: 13 });
    localStorage.removeItem('id');
  });

  it('o município escolhido no filtro ganha do cadastro', async () => {
    sut.choice = { city: 'Santa Rita do Sapucaí', state: 'MG' };
    geocoding.geocode.and.resolveTo(ITAJUBA);

    await sut.mapCenter();

    expect(geocoding.geocode).toHaveBeenCalledWith('', 'Santa Rita do Sapucaí', 'MG');
    expect(userRead.findById).not.toHaveBeenCalled();
  });

  it('sem escolha e sem cadastro, devolve null e o mapa fica onde está', async () => {
    localStorage.removeItem('id');

    expect(await sut.mapCenter()).toBeNull();
  });
});

describe('LocalityPreferenceService – mapa: conta primeiro, GPS depois', () => {
  let sut: LocalityPreferenceService;
  let geocoding: jasmine.SpyObj<GeocodingService>;
  let userRead: jasmine.SpyObj<UserReadService>;

  beforeEach(() => {
    localStorage.removeItem('locality.choice');
    localStorage.removeItem('id');
    geocoding = jasmine.createSpyObj('GeocodingService', ['geocode', 'reverseGeocode']);
    userRead  = jasmine.createSpyObj('UserReadService', ['findById']);
    TestBed.configureTestingModule({
      providers: [
        LocalityPreferenceService,
        { provide: GeocodingService, useValue: geocoding },
        { provide: UserReadService,  useValue: userRead },
        { provide: ToastrService,    useValue: jasmine.createSpyObj('ToastrService', ['warning', 'info']) },
      ]
    });
    sut = TestBed.inject(LocalityPreferenceService);
  });

  afterEach(() => { localStorage.removeItem('locality.choice'); localStorage.removeItem('id'); });

  it('com conta, abre no município do cadastro mesmo havendo outra escolha salva', async () => {
    sut.choice = { city: 'Pouso Alegre', state: 'MG' };
    localStorage.setItem('id', '7');
    userRead.findById.and.resolveTo({ city: 'Itajubá', state: 'mg' } as any);

    expect(await sut.startingMunicipality()).toEqual({ city: 'Itajubá', state: 'MG' });
    expect(sut.choice).toEqual({ city: 'Itajubá', state: 'MG' });
  });

  it('visitante (conta anônima) abre sempre em Santa Rita do Sapucaí/MG', async () => {
    sut.choice = { city: 'Pouso Alegre', state: 'MG' };

    expect(await sut.startingMunicipality(true)).toEqual({ city: 'Santa Rita do Sapucaí', state: 'MG' });
    expect(sut.choice).toEqual({ city: 'Santa Rita do Sapucaí', state: 'MG' });
    expect(userRead.findById).not.toHaveBeenCalled();
  });

  it('conta sem município cadastrado usa a última escolha salva', async () => {
    sut.choice = { city: 'Pouso Alegre', state: 'MG' };
    localStorage.setItem('id', '1');
    userRead.findById.and.resolveTo({ city: null } as any);

    expect(await sut.startingMunicipality()).toEqual({ city: 'Pouso Alegre', state: 'MG' });
  });

  it('descobre o município de uma posição do GPS e passa a lembrá-lo', async () => {
    geocoding.reverseGeocode.and.resolveTo({ city: 'Santa Rita do Sapucaí', state: 'MG' } as any);

    expect(await sut.municipalityAt(-22.25, -45.70)).toEqual({ city: 'Santa Rita do Sapucaí', state: 'MG' });
    expect(sut.choice).toEqual({ city: 'Santa Rita do Sapucaí', state: 'MG' });
  });

  it('posição sem município conhecido não muda a escolha', async () => {
    sut.choice = { city: 'Itajubá', state: 'MG' };
    geocoding.reverseGeocode.and.resolveTo(null as any);

    expect(await sut.municipalityAt(0, 0)).toBeNull();
    expect(sut.choice).toEqual({ city: 'Itajubá', state: 'MG' });
  });
});
