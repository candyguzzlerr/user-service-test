package com.userservice.service;

import com.userservice.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    User createUser(String name, String email, int age);
    Optional<User> getUserById(Long id);
    List<User> getAllUsers();
    User updateUser(Long id, String name, String email, Integer age);
    boolean deleteUser(Long id);
}
