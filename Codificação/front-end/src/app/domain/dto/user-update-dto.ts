export interface UpdatePasswordDto {
    id: string;
    oldPassword: string;
    newPassword: string;
}

/** RF04: dados de perfil editáveis — PUT /api/user/{id}. */
export interface UpdateProfileDto {
    id: number;
    fullname: string;
    phoneNumber?: string;
    street?: string;
    neighborhood?: string;
    number?: string;
    cep?: string;
    city?: string;
}