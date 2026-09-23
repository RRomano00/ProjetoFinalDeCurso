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
