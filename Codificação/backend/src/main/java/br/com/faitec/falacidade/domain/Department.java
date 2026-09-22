package br.com.faitec.falacidade.domain;

/**
 * RF22: departamento (secretaria) da prefeitura para o qual a ocorrência é
 * encaminhada. O e-mail é o endereço de destino do encaminhamento, por isso
 * é obrigatório e único, assim como o nome.
 */
public class Department {

    private int id;
    private String name;
    private String email;
    /** Município e UF do setor: o encaminhamento segue o endereço da ocorrência. */
    private String city;
    private String state;

    public Department() {}

    public Department(int id, String name, String email, String city, String state) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.city = city;
        setState(state);
    }

    public int    getId()               { return id; }
    public void   setId(int id)         { this.id = id; }
    public String getName()             { return name; }
    public void   setName(String name)  { this.name = name; }
    public String getEmail()            { return email; }
    public void   setEmail(String email){ this.email = email; }
    public String getCity()             { return city; }
    public void   setCity(String city)  { this.city = city == null ? null : city.trim(); }
    public String getState()            { return state; }
    /** Guarda em maiúsculas: a UF é comparada com a do endereço da ocorrência. */
    public void   setState(String state) {
        this.state = state == null || state.isBlank() ? null : state.trim().toUpperCase();
    }
}
