package br.com.faitec.falacidade.domain;

public class Department {

    private int id;
    private String name;
    private String email;
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
    public void   setState(String state) {
        this.state = state == null || state.isBlank() ? null : state.trim().toUpperCase();
    }
}
