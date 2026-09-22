package br.com.faitec.falacidade.port.service.department;

import br.com.faitec.falacidade.domain.Department;

import java.util.List;

public interface DepartmentService {

    /**
     * Cadastra o departamento.
     * @throws IllegalArgumentException dados obrigatórios ausentes ou inválidos
     * @throws IllegalStateException    nome ou e-mail já cadastrados
     */
    int create(Department entity);

    /**
     * Edita nome, e-mail e município do setor.
     * @throws IllegalArgumentException dados obrigatórios ausentes ou inválidos
     * @throws IllegalStateException    nome ou e-mail já são de outro setor
     */
    void update(int id, Department entity);

    List<Department> findAll();

    /** Setores do município — é por ele que o encaminhamento é oferecido. */
    List<Department> findAllByCity(String city, String state);

    Department findById(int id);
}
