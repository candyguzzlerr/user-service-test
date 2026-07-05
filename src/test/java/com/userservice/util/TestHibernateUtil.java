package com.userservice.util;

import com.userservice.entity.User;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

/**
 * Builds a standalone Hibernate {@link SessionFactory} pointed at an arbitrary
 * JDBC connection (used to wire up a Testcontainers PostgreSQL instance),
 * fully independent from the production {@link HibernateUtil} singleton so
 * that test runs never share state with each other or with application code.
 */
public final class TestHibernateUtil {

    private TestHibernateUtil() {}

    public static SessionFactory buildSessionFactory(String jdbcUrl, String username, String password) {
        return new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                .setProperty("hibernate.connection.url", jdbcUrl)
                .setProperty("hibernate.connection.username", username)
                .setProperty("hibernate.connection.password", password)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "true")
                .setProperty("hibernate.format_sql", "true")
                .addAnnotatedClass(User.class)
                .buildSessionFactory();
    }
}
