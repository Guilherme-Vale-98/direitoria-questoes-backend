package com.direitoria.questoes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaSmokeTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void drizzleSchemaIsLoaded() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_name in ('questao','disciplina','banca','orgao',"
                        + "'questao_disciplina','users','roles','user_role_junction')",
                Integer.class);
        assertEquals(8, tables);
    }
}
