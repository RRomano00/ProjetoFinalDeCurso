package br.com.faitec.falacidade.port.dao.department;

import br.com.faitec.falacidade.domain.Department;

import java.util.List;

public interface DepartmentDao {

    int add(Department entity);

    boolean update(Department entity);

    List<Department> readall();

    List<Department> readAllByCity(String city, String state);

    Department readById(int id);

    boolean existsByNameInCity(String name, String city, String state);

    boolean existsByEmail(String email);
}
