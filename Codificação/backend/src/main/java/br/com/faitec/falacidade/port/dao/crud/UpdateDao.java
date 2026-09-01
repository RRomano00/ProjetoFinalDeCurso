package br.com.faitec.falacidade.port.dao.crud;

public interface UpdateDao <T>{

    void updateInformation(final int id, final T entity);

}
