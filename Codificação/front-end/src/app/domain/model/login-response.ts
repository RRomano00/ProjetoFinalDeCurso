/** Resposta de POST /authenticate e /authenticate/mfa — espelha LoginResponseDto.java. */
export interface LoginResponse {
  token: string | null;
  requiresMfa: boolean;
  mfaToken: string | null;
  mfaAppAvailable: boolean;
  mfaEmailAvailable: boolean;
  mfaSmsAvailable: boolean;
}
