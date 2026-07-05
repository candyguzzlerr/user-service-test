package com.userservice.service;

import com.userservice.dao.UserDao;
import com.userservice.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+\\-]+@[\\w\\-]+\\.[a-zA-Z]{2,}$");
    private static final int MIN_AGE = 0;
    private static final int MAX_AGE = 150;

    private final UserDao userDao;

    public UserServiceImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public User createUser(String name, String email, int age) {
        validateName(name);
        validateEmail(email);
        validateAge(age);

        if (userDao.existsByEmail(email)) {
            throw new UserValidationException("Пользователь с email '" + email + "' уже существует");
        }

        User user = new User(name, email, age);
        User saved = userDao.save(user);
        log.info("User created: id={}", saved.getId());
        return saved;
    }

    @Override
    public Optional<User> getUserById(Long id) {
        if (id == null) {
            throw new UserValidationException("ID не может быть null");
        }
        return userDao.findById(id);
    }

    @Override
    public List<User> getAllUsers() {
        return userDao.findAll();
    }

    @Override
    public User updateUser(Long id, String name, String email, Integer age) {
        if (id == null) {
            throw new UserValidationException("ID не может быть null");
        }

        User user = userDao.findById(id)
                .orElseThrow(() -> new UserValidationException("Пользователь с id=" + id + " не найден"));

        if (name != null && !name.isBlank()) {
            validateName(name);
            user.setName(name);
        }
        if (email != null && !email.isBlank()) {
            validateEmail(email);
            if (!email.equals(user.getEmail()) && userDao.existsByEmail(email)) {
                throw new UserValidationException("Email '" + email + "' уже используется другим пользователем");
            }
            user.setEmail(email);
        }
        if (age != null) {
            validateAge(age);
            user.setAge(age);
        }

        User updated = userDao.update(user);
        log.info("User updated: id={}", updated.getId());
        return updated;
    }

    @Override
    public boolean deleteUser(Long id) {
        if (id == null) {
            throw new UserValidationException("ID не может быть null");
        }
        boolean deleted = userDao.delete(id);
        log.info("User delete id={} -> {}", id, deleted);
        return deleted;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new UserValidationException("Имя не может быть пустым");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new UserValidationException("Некорректный формат email: " + email);
        }
    }

    private void validateAge(int age) {
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new UserValidationException("Возраст должен быть от " + MIN_AGE + " до " + MAX_AGE);
        }
    }
}
