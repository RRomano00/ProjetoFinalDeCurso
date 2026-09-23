package br.com.faitec.falacidade.port.service.department;

import br.com.faitec.falacidade.domain.Department;

import java.util.List;

public interface DepartmentService {

    int create(Department entity);

    void update(int id, Department entity);

    List<Department> findAll();

    List<Department> findAllByCity(String city, String state);

    Department findById(int id);
}
