package br.com.faitec.falacidade.controller;

import br.com.faitec.falacidade.domain.Department;
import br.com.faitec.falacidade.domain.Municipality;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.department.CreateDepartmentDto;
import br.com.faitec.falacidade.port.service.department.DepartmentService;
import br.com.faitec.falacidade.port.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/department")
public class DepartmentRestController {

    private final DepartmentService departmentService;
    private final UserService userService;

    public DepartmentRestController(DepartmentService departmentService, UserService userService) {
        this.departmentService = departmentService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<Department>> getAll(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            Authentication auth) {

        UserModel user = currentUser(auth);

        // Para a equipe o filtro city/state é ignorado de propósito: a ocorrência
        // que ela encaminha é sempre do seu município, então honrá-lo só serviria
        // para enxergar os setores de outra prefeitura trocando a consulta.
        if (isSuperAdmin(user))
            return ResponseEntity.ok(city != null && !city.isBlank() && state != null && !state.isBlank()
                ? departmentService.findAllByCity(city, state)
                : departmentService.findAll());

        return ResponseEntity.ok(user == null
            ? List.of()
            : departmentService.findAllByCity(user.getCity(), user.getState()));
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateDepartmentDto dto, Authentication auth) {
        UserModel user = currentUser(auth);
        Department entity = dto.toDepartment();

        if (!isSuperAdmin(user) && !sameCity(user, entity.getCity(), entity.getState()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Você só pode cadastrar departamentos do seu município."));

        try {
            int id = departmentService.create(entity);
            entity.setId(id);
            return ResponseEntity.status(HttpStatus.CREATED).body(entity);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id,
                                    @Valid @RequestBody CreateDepartmentDto dto,
                                    Authentication auth) {
        Department atual = departmentService.findById(id);
        if (atual == null) return ResponseEntity.notFound().build();

        UserModel user = currentUser(auth);
        Department entity = dto.toDepartment();

        if (!isSuperAdmin(user) && (!sameCity(user, atual.getCity(), atual.getState())
                                 || !sameCity(user, entity.getCity(), entity.getState())))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Você só pode editar departamentos do seu município."));

        try {
            departmentService.update(id, entity);
            return ResponseEntity.ok(entity);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private UserModel currentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return userService.findByEmail(auth.getName()); }
        catch (Exception e) { return null; }
    }

    private boolean isSuperAdmin(UserModel user) {
        return user != null && user.isSuperAdmin();
    }

    private boolean sameCity(UserModel user, String city, String state) {
        return user != null && Municipality.same(user.getCity(), user.getState(), city, state);
    }
}
