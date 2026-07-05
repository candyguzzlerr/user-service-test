package com.userservice.dao;

import com.userservice.entity.User;
import java.util.List;
import java.util.Optional;

public interface UserDao {
    User save(User user);
    Optional<User> findById(Long id);
    List<User> findAll();
    User update(User user);
    boolean delete(Long id);
    boolean existsByEmail(String email);
}
