package br.com.faitec.falacidade.implementation.service.user;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.dao.user.UserDao;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserDao userDao, PasswordEncoder passwordEncoder) {
        this.userDao         = userDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public int create(UserModel entity) {
        if (entity == null) return -1;
        if (isBlank(entity.getFullname()) || isBlank(entity.getEmail())) return -1;

        if (entity.getRole() == UserModel.UserRole.CITIZEN && !entity.isAcceptsTerms()) return -1;

        if (!isPasswordValid(entity.getPassword())) return -1;

        entity.setPassword(passwordEncoder.encode(entity.getPassword()));

        return userDao.add(entity);
    }

    @Override
    public void delete(int id) {
        if (id < 0) return;
        userDao.remove(id);
    }

    @Override
    public UserModel findById(int id) {
        if (id < 0) return null;
        return userDao.readById(id);
    }

    @Override
    public List<UserModel> findAll() {
        return userDao.readall();
    }

    @Override
    public List<UserModel> findAllUsers() {
        return userDao.readAllUsers();
    }

    @Override
    public void setActive(int userId, boolean active) {
        if (userId > 0) userDao.setActive(userId, active);
    }

    @Override
    public void update(int id, UserModel entity) {
        if (id != entity.getId()) return;
        if (findById(id) == null) return;
        userDao.updateInformation(id, entity);
    }

    @Override
    public UserModel findByEmail(String email) {
        if (isBlank(email)) return null;
        return userDao.readByEmail(email);
    }

    @Override
    public boolean updatePassword(int id, String oldPassword, String newPassword) {
        UserModel user = findById(id);
        if (user == null) return false;
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) return false;
        if (!isPasswordValid(newPassword)) return false;
        return userDao.updatePassword(id, passwordEncoder.encode(newPassword));
    }

    @Override
    public boolean updatePasswordEncoded(int userId, String encodedPassword) {
        if (userId < 0 || isBlank(encodedPassword)) return false;
        return userDao.updatePassword(userId, encodedPassword);
    }

    private boolean isPasswordValid(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasLetter  = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit   = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        return hasLetter && hasDigit && hasSpecial;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
