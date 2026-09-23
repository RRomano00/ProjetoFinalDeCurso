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

/**
 * RF22: departamentos da prefeitura — destinos possíveis do encaminhamento.
 *
 * O departamento é municipal: o funcionário administra os setores do próprio
 * município e o administrador, os de todos. A restrição de perfil está em
 * JwtSecurityConfiguration; a de município é aplicada aqui.
 */
@RestController
@RequestMapping("/api/department")
public class DepartmentRestController {

    private final DepartmentService departmentService;
    private final UserService userService;

    public DepartmentRestController(DepartmentService departmentService, UserService userService) {
        this.departmentService = departmentService;
        this.userService = userService;
    }

    /**
     * Lista os setores do município de quem consulta. O filtro city/state é do
     * Super Administrador, que não tem município próprio e precisa alcançar
     * qualquer um.
     *
     * Para a equipe o parâmetro é ignorado de propósito: a ocorrência que ela
     * encaminha é sempre do seu município (RN07, conferido em
     * OccurrenceRestController), então o único efeito de honrar o filtro seria
     * deixar qualquer funcionário enxergar os setores de outra prefeitura
     * trocando a consulta na barra de endereços.
     */
    @GetMapping
    public ResponseEntity<List<Department>> getAll(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            Authentication auth) {

        UserModel user = currentUser(auth);

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

        // A equipe cadastra setores do próprio município; só o Super
        // Administrador, sem município, cadastra em qualquer um. Sem essa trava,
        // um município poderia receber destinos de encaminhamento criados por outro.
        if (!isSuperAdmin(user) && !sameCity(user, entity.getCity(), entity.getState()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Você só pode cadastrar departamentos do seu município."));

        try {
            // O serviço apara o nome e normaliza e-mail e UF; a resposta devolve os
            // valores gravados, e não os digitados, para a tela não exibir uma
            // grafia diferente da que está no banco.
            int id = departmentService.create(entity);
            entity.setId(id);
            return ResponseEntity.status(HttpStatus.CREATED).body(entity);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * RF22/RF25: o administrador corrige nome, e-mail e município do setor.
     * Precisa alcançar os dois municípios — o de origem e o de destino —, senão
     * mudaria o setor de outra cidade, ou mandaria o seu para fora do alcance.
     */
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
            // Devolve o que foi gravado, e não o digitado: a tela mostra a grafia do banco.
            return ResponseEntity.ok(entity);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ---- helpers ----

    private UserModel currentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return userService.findByEmail(auth.getName()); }
        catch (Exception e) { return null; }
    }

    /** Sem município próprio, o Super Administrador vê e cadastra em todos (RF25). */
    private boolean isSuperAdmin(UserModel user) {
        return user != null && user.isSuperAdmin();
    }

    private boolean sameCity(UserModel user, String city, String state) {
        return user != null && Municipality.same(user.getCity(), user.getState(), city, state);
    }
}
