import { UserRole } from "./user-role";

export interface User {
    id?: string,
    fullname: string,
    email: string,
    password: string,
    role: UserRole,
    /** Município e UF: de atuação, para a equipe; de residência, para o cidadão. */
    city?: string,
    state?: string
}