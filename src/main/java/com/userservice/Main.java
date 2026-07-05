package com.userservice;

import com.userservice.dao.DaoException;
import com.userservice.dao.UserDao;
import com.userservice.dao.UserDaoImpl;
import com.userservice.entity.User;
import com.userservice.service.UserService;
import com.userservice.service.UserServiceImpl;
import com.userservice.service.UserValidationException;
import com.userservice.util.HibernateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final UserDao userDao = new UserDaoImpl();
    private static final UserService userService = new UserServiceImpl(userDao);
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        log.info("User Service started");
        System.out.println("=".repeat(50));
        System.out.println("       USER SERVICE — Управление пользователями");
        System.out.println("=".repeat(50));

        try {
            boolean running = true;
            while (running) {
                printMenu();
                String input = scanner.nextLine().trim();
                switch (input) {
                    case "1" -> createUser();
                    case "2" -> findUserById();
                    case "3" -> listAllUsers();
                    case "4" -> updateUser();
                    case "5" -> deleteUser();
                    case "0" -> running = false;
                    default -> System.out.println("[!] Неверный выбор. Попробуйте снова.");
                }
            }
        } finally {
            HibernateUtil.shutdown();
            scanner.close();
            System.out.println("До свидания!");
            log.info("User Service stopped");
        }
    }

    // ─── Menu ────────────────────────────────────────────────────────────────

    private static void printMenu() {
        System.out.println();
        System.out.println("Выберите действие:");
        System.out.println("  1. Создать пользователя");
        System.out.println("  2. Найти пользователя по ID");
        System.out.println("  3. Список всех пользователей");
        System.out.println("  4. Обновить пользователя");
        System.out.println("  5. Удалить пользователя");
        System.out.println("  0. Выход");
        System.out.print("> ");
    }

    // ─── CRUD operations ─────────────────────────────────────────────────────

    private static void createUser() {
        System.out.println("\n--- Создание пользователя ---");
        String name = promptNonEmpty("Имя: ");
        String email = promptNonEmpty("Email: ");
        Integer age = promptInt("Возраст: ");
        if (age == null) return;

        try {
            User saved = userService.createUser(name, email, age);
            System.out.printf("[+] Пользователь создан: %s%n", saved);
        } catch (UserValidationException | DaoException e) {
            System.out.println("[!] Ошибка: " + e.getMessage());
        }
    }

    private static void findUserById() {
        System.out.println("\n--- Поиск пользователя ---");
        Long id = promptLong("Введите ID: ");
        if (id == null) return;

        try {
            Optional<User> result = userService.getUserById(id);
            if (result.isPresent()) {
                printUser(result.get());
            } else {
                System.out.printf("[!] Пользователь с ID=%d не найден.%n", id);
            }
        } catch (UserValidationException | DaoException e) {
            System.out.println("[!] Ошибка: " + e.getMessage());
        }
    }

    private static void listAllUsers() {
        System.out.println("\n--- Список пользователей ---");
        try {
            List<User> users = userService.getAllUsers();
            if (users.isEmpty()) {
                System.out.println("Пользователи не найдены.");
            } else {
                System.out.printf("%-6s %-20s %-30s %-5s %-20s%n",
                        "ID", "Имя", "Email", "Возраст", "Дата создания");
                System.out.println("-".repeat(85));
                users.forEach(u -> System.out.printf(
                        "%-6d %-20s %-30s %-5d %-20s%n",
                        u.getId(), u.getName(), u.getEmail(), u.getAge(), u.getCreatedAt()));
            }
        } catch (DaoException e) {
            System.out.println("[!] Ошибка: " + e.getMessage());
        }
    }

    private static void updateUser() {
        System.out.println("\n--- Обновление пользователя ---");
        Long id = promptLong("ID пользователя для обновления: ");
        if (id == null) return;

        try {
            Optional<User> existing = userService.getUserById(id);
            if (existing.isEmpty()) {
                System.out.printf("[!] Пользователь с ID=%d не найден.%n", id);
                return;
            }

            User user = existing.get();
            System.out.println("Текущие данные: " + user);
            System.out.println("(Оставьте поле пустым, чтобы не изменять)");

            String name = promptOptional("Новое имя [" + user.getName() + "]: ");
            String email = promptOptional("Новый email [" + user.getEmail() + "]: ");
            String ageStr = promptOptional("Новый возраст [" + user.getAge() + "]: ");

            Integer age = null;
            if (!ageStr.isEmpty()) {
                try {
                    age = Integer.parseInt(ageStr);
                } catch (NumberFormatException e) {
                    System.out.println("[!] Некорректный возраст. Обновление отменено.");
                    return;
                }
            }

            User updated = userService.updateUser(
                    id,
                    name.isEmpty() ? null : name,
                    email.isEmpty() ? null : email,
                    age);
            System.out.println("[+] Пользователь обновлён: " + updated);
        } catch (UserValidationException | DaoException e) {
            System.out.println("[!] Ошибка: " + e.getMessage());
        }
    }

    private static void deleteUser() {
        System.out.println("\n--- Удаление пользователя ---");
        Long id = promptLong("ID пользователя для удаления: ");
        if (id == null) return;

        System.out.print("Вы уверены? (y/N): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println("Отменено.");
            return;
        }

        try {
            boolean deleted = userService.deleteUser(id);
            if (deleted) {
                System.out.printf("[+] Пользователь с ID=%d удалён.%n", id);
            } else {
                System.out.printf("[!] Пользователь с ID=%d не найден.%n", id);
            }
        } catch (UserValidationException | DaoException e) {
            System.out.println("[!] Ошибка: " + e.getMessage());
        }
    }

    // ─── Input helpers ────────────────────────────────────────────────────────

    private static void printUser(User u) {
        System.out.println("-".repeat(40));
        System.out.printf("  ID:           %d%n", u.getId());
        System.out.printf("  Имя:          %s%n", u.getName());
        System.out.printf("  Email:        %s%n", u.getEmail());
        System.out.printf("  Возраст:      %d%n", u.getAge());
        System.out.printf("  Создан:       %s%n", u.getCreatedAt());
        System.out.println("-".repeat(40));
    }

    private static String promptNonEmpty(String prompt) {
        String value;
        do {
            System.out.print(prompt);
            value = scanner.nextLine().trim();
            if (value.isEmpty()) System.out.println("[!] Поле не может быть пустым.");
        } while (value.isEmpty());
        return value;
    }

    private static String promptOptional(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static Long promptLong(String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            System.out.println("[!] Некорректный ID. Ожидается целое число.");
            return null;
        }
    }

    private static Integer promptInt(String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("[!] Некорректное число.");
            return null;
        }
    }
}
