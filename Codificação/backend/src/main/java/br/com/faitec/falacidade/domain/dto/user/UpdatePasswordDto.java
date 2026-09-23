package br.com.faitec.falacidade.domain.dto.user;

public class UpdatePasswordDto {
    private int    id;
    private String oldPassword;
    private String newPassword;

    public int    getId()           { return id; }
    public void   setId(int v)      { this.id = v; }
    public String getOldPassword()  { return oldPassword; }
    public void   setOldPassword(String v)  { this.oldPassword = v; }
    public String getNewPassword()  { return newPassword; }
    public void   setNewPassword(String v)  { this.newPassword = v; }
}
