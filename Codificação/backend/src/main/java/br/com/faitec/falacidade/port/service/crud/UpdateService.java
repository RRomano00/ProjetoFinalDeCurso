package br.com.faitec.falacidade.port.service.crud;

public interface UpdateService <T>{

    void update(final int id, final T entity);

}
