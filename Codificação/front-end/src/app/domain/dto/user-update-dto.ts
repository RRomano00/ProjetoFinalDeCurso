export interface UpdatePasswordDto {
    id: string;
    oldPassword: string;
    newPassword: string;
}

export interface UpdateProfileDto {
    id: number;
    fullname: string;
    phoneNumber?: string;
    street?: string;
    neighborhood?: string;
    number?: string;
    cep?: string;
    state?: string;
    city?: string;
}
