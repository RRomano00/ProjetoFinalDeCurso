/** RF22: departamento (secretaria) para o qual a ocorrência é encaminhada. */
export interface Department {
  id: number;
  name: string;
  email: string;
  /** Município e UF do setor: o encaminhamento segue o endereço da ocorrência. */
  city: string;
  state: string;
}
