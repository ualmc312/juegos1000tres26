package com.juegos1000tres.juegos1000tres_backend.config;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UsuarioSchemaFixer {

    private final JdbcTemplate jdbcTemplate;

    public UsuarioSchemaFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dropNombreUniqueConstraintIfPresent() {
        String sql = """
            SELECT tc.constraint_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
             AND tc.table_schema = kcu.table_schema
            WHERE tc.table_name = 'usuarios'
              AND tc.constraint_type = 'UNIQUE'
              AND kcu.column_name = 'nombre'
            """;

        List<String> constraintNames = jdbcTemplate.queryForList(sql, String.class);
        for (String constraintName : constraintNames) {
            jdbcTemplate.execute("ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS \"" + constraintName + "\"");
        }
    }
}