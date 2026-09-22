package br.com.faitec.falacidade.implementation.service.department;

import br.com.faitec.falacidade.domain.Department;
import br.com.faitec.falacidade.port.dao.department.DepartmentDao;
import br.com.faitec.falacidade.port.service.department.DepartmentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");

    private final DepartmentDao departmentDao;

    public DepartmentServiceImpl(DepartmentDao departmentDao) {
        this.departmentDao = departmentDao;
    }

    @Override
    public int create(Department entity) {
        normalize(entity);

        // O nome se repete entre municípios — cada prefeitura tem a sua Secretaria
        // de Obras —, mas o e-mail é o destino do encaminhamento e é único.
        if (departmentDao.existsByNameInCity(entity.getName(), entity.getCity(), entity.getState()))
            throw new IllegalStateException("Já existe um departamento com este nome neste município");
        if (departmentDao.existsByEmail(entity.getEmail()))
            throw new IllegalStateException("Já existe um departamento com este e-mail");

        int id = departmentDao.add(entity);
        // −1 só acontece quando a restrição do banco barra uma duplicata que passou
        // pela verificação acima (duas requisições simultâneas).
        if (id < 0) throw new IllegalStateException("Já existe um departamento com este nome ou e-mail");
        return id;
    }

    /**
     * RF22: o administrador municipal corrige o setor. A duplicidade fica a cargo
     * da restrição do banco: ao editar, ela é a única que isenta o próprio
     * registro — uma consulta prévia acusaria o setor contra ele mesmo.
     */
    @Override
    public void update(int id, Department entity) {
        if (id <= 0) throw new IllegalArgumentException("Departamento inexistente");
        normalize(entity);
        entity.setId(id);
        if (!departmentDao.update(entity))
            throw new IllegalStateException("Já existe um departamento com este nome ou e-mail");
    }

    /** Apara e confere o que é obrigatório, gravando os valores já normalizados. */
    private void normalize(Department entity) {
        if (entity == null) throw new IllegalArgumentException("Departamento não pode ser nulo");

        String name  = entity.getName()  == null ? "" : entity.getName().trim();
        String email = entity.getEmail() == null ? "" : entity.getEmail().trim().toLowerCase();
        String city  = entity.getCity()  == null ? "" : entity.getCity().trim();
        String state = entity.getState() == null ? "" : entity.getState().trim().toUpperCase();

        if (name.isBlank())  throw new IllegalArgumentException("Nome do departamento é obrigatório");
        if (email.isBlank()) throw new IllegalArgumentException("E-mail do departamento é obrigatório");
        if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("E-mail inválido");
        if (city.isBlank())  throw new IllegalArgumentException("Município do departamento é obrigatório");
        if (state.length() != 2) throw new IllegalArgumentException("UF do departamento é obrigatória");

        entity.setName(name);
        entity.setEmail(email);
        entity.setCity(city);
        entity.setState(state);
    }

    @Override
    public List<Department> findAll() {
        return departmentDao.readall();
    }

    @Override
    public List<Department> findAllByCity(String city, String state) {
        if (city == null || city.isBlank() || state == null || state.isBlank()) return List.of();
        return departmentDao.readAllByCity(city.trim(), state.trim());
    }

    @Override
    public Department findById(int id) {
        return id > 0 ? departmentDao.readById(id) : null;
    }
}
