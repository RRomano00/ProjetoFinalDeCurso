import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface ReverseAddress {
  street: string;
  neighborhood: string;
  city: string;
}

@Injectable({ providedIn: 'root' })
export class GeocodingService {
  constructor(private http: HttpClient) { }

  /** Endereço -> coordenadas (geocoding direto). */
  async geocode(street: string, neighborhood: string, city: string): Promise<{ lat: number; lng: number } | null> {
    const query = encodeURIComponent(`${street}, ${neighborhood}, ${city}, Brasil`);
    const url = `https://nominatim.openstreetmap.org/search?q=${query}&format=json&limit=1`;

    try {
      const results: any[] = await firstValueFrom(
        this.http.get<any[]>(url, { headers: { 'Accept-Language': 'pt-BR' } })
      );
      if (results && results.length > 0) {
        return { lat: parseFloat(results[0].lat), lng: parseFloat(results[0].lon) };
      }
    } catch { }
    return null;
  }

  /** Coordenadas -> endereço (reverse geocoding). Usado ao clicar no mapa (RF09). */
  async reverseGeocode(lat: number, lng: number): Promise<ReverseAddress | null> {
    const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`;
    try {
      const res: any = await firstValueFrom(
        this.http.get<any>(url, { headers: { 'Accept-Language': 'pt-BR' } })
      );
      const a = res?.address;
      if (!a) return null;
      return {
        street:       a.road || a.pedestrian || a.residential || a.footway || a.path || '',
        neighborhood: a.suburb || a.neighbourhood || a.city_district || a.quarter || '',
        city:         a.city || a.town || a.village || a.municipality || ''
      };
    } catch { }
    return null;
  }
}
