package br.com.faitec.falacidade.domain.dto.user;

import br.com.faitec.falacidade.domain.UserModel;

/** Payload de atualização de perfil — PUT /api/user/{id}. */
public class UpdateUserDto {
    private int id;
    private String fullname, phoneNumber, street, neighborhood, number, cep, city;

    public UserModel toUserModel() {
        UserModel u = new UserModel();
        u.setId(id);             u.setFullname(fullname);
        u.setPhoneNumber(phoneNumber); u.setStreet(street);
        u.setNeighborhood(neighborhood); u.setNumber(number);
        u.setCep(cep);           u.setCity(city);
        return u;
    }

    public int    getId()           { return id; }
    public void   setId(int v)      { this.id = v; }
    public String getFullname()     { return fullname; }
    public void   setFullname(String v)     { this.fullname = v; }
    public String getPhoneNumber()  { return phoneNumber; }
    public void   setPhoneNumber(String v)  { this.phoneNumber = v; }
    public String getStreet()       { return street; }
    public void   setStreet(String v)       { this.street = v; }
    public String getNeighborhood() { return neighborhood; }
    public void   setNeighborhood(String v) { this.neighborhood = v; }
    public String getNumber()       { return number; }
    public void   setNumber(String v)       { this.number = v; }
    public String getCep()          { return cep; }
    public void   setCep(String v)          { this.cep = v; }
    public String getCity()         { return city; }
    public void   setCity(String v)         { this.city = v; }
}
