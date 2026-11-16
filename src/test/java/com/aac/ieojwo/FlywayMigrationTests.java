package com.aac.ieojwo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flyway-validation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void migrationsMatchJpaMappings() {
        Integer daysLength = jdbcTemplate.queryForObject("""
                select character_maximum_length
                from information_schema.columns
                where lower(table_name) = 'routines' and lower(column_name) = 'days_of_week'
                """, Integer.class);
        Integer categoryCount = jdbcTemplate.queryForObject(
                "select count(*) from symbol_categories where active = true", Integer.class);
        Integer symbolCount = jdbcTemplate.queryForObject(
                "select count(*) from symbols where active = true", Integer.class);

        assertThat(daysLength).isGreaterThanOrEqualTo(80);
        assertThat(categoryCount).isGreaterThanOrEqualTo(9);
        assertThat(symbolCount).isGreaterThanOrEqualTo(19);
    }
}
