package br.com.faitec.falacidade.port.dao.department;

import br.com.faitec.falacidade.domain.Department;

import java.util.List;

public interface DepartmentDao {

    /** Devolve o id gerado ou −1 quando o nome ou o e-mail já existem. */
    int add(Department entity);

    /** Grava a edição; false quando o nome ou o e-mail já são de outro setor. */
    boolean update(Department entity);

    List<Department> readall();

    /** Setores de um município — o leque de encaminhamento e a lista da equipe. */
    List<Department> readAllByCity(String city, String state);

    Department readById(int id);

    /** O nome se repete entre municípios; dentro do mesmo, não. */
    boolean existsByNameInCity(String name, String city, String state);

    boolean existsByEmail(String email);
}
