package com.userservice.dao;

import com.userservice.entity.User;
import com.userservice.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class UserDaoImpl implements UserDao {

    private static final Logger log = LoggerFactory.getLogger(UserDaoImpl.class);
    private final SessionFactory sessionFactory;

    public UserDaoImpl() {
        this.sessionFactory = HibernateUtil.getSessionFactory();
    }

    public UserDaoImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public User save(User user) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(user);
            tx.commit();
            log.debug("Saved user: {}", user);
            return user;
        } catch (ConstraintViolationException e) {
            rollback(tx);
            log.warn("Constraint violation while saving user with email '{}': {}", user.getEmail(), e.getMessage());
            throw new DaoException("Email уже используется: " + user.getEmail(), e);
        } catch (Exception e) {
            rollback(tx);
            log.error("Error saving user", e);
            throw new DaoException("Ошибка при создании пользователя", e);
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            User user = session.get(User.class, id);
            log.debug("findById({}) -> {}", id, user);
            return Optional.ofNullable(user);
        } catch (Exception e) {
            log.error("Error finding user by id {}", id, e);
            throw new DaoException("Ошибка при поиске пользователя с id=" + id, e);
        }
    }

    @Override
    public List<User> findAll() {
        try (Session session = sessionFactory.openSession()) {
            List<User> users = session.createQuery("FROM User ORDER BY id", User.class).list();
            log.debug("findAll() -> {} records", users.size());
            return users;
        } catch (Exception e) {
            log.error("Error fetching all users", e);
            throw new DaoException("Ошибка при получении списка пользователей", e);
        }
    }

    @Override
    public User update(User user) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            User merged = session.merge(user);
            tx.commit();
            log.debug("Updated user: {}", merged);
            return merged;
        } catch (ConstraintViolationException e) {
            rollback(tx);
            log.warn("Constraint violation while updating user id={}: {}", user.getId(), e.getMessage());
            throw new DaoException("Email уже используется другим пользователем: " + user.getEmail(), e);
        } catch (Exception e) {
            rollback(tx);
            log.error("Error updating user id={}", user.getId(), e);
            throw new DaoException("Ошибка при обновлении пользователя", e);
        }
    }

    @Override
    public boolean delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            User user = session.get(User.class, id);
            if (user == null) {
                tx.rollback();
                log.debug("delete({}) -> not found", id);
                return false;
            }
            session.remove(user);
            tx.commit();
            log.debug("Deleted user id={}", id);
            return true;
        } catch (Exception e) {
            rollback(tx);
            log.error("Error deleting user id={}", id, e);
            throw new DaoException("Ошибка при удалении пользователя с id=" + id, e);
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        try (Session session = sessionFactory.openSession()) {
            Long count = session.createQuery(
                    "SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                    .setParameter("email", email)
                    .uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking email existence: {}", email, e);
            throw new DaoException("Ошибка при проверке email", e);
        }
    }

    private void rollback(Transaction tx) {
        if (tx != null && tx.isActive()) {
            try {
                tx.rollback();
            } catch (Exception ex) {
                log.error("Transaction rollback failed", ex);
            }
        }
    }
}
