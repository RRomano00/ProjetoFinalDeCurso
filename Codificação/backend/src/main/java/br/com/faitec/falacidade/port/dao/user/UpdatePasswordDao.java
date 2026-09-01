package br.com.faitec.falacidade.port.dao.user;

public interface UpdatePasswordDao {

    boolean updatePassword(final int id, final String newPassword);

}
