package br.com.betufu.platform.persistence;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresAuthorityProbe {

    private final DataSource dataSource;

    public PostgresAuthorityProbe(@Autowired(required = false) DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public PostgresAuthorityStatus checkAuthority() {
        if (dataSource == null) {
            return new PostgresAuthorityStatus(false, 0, 0);
        }

        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            Integer majorVersion = jdbcTemplate.queryForObject(
                    "SELECT current_setting('server_version_num')::integer / 10000",
                    Integer.class);

            Integer guardCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM public.platform_guard WHERE guard_key = 'PLATFORM_SCHEMA_AUTHORITATIVE'",
                    Integer.class);

            boolean healthy = majorVersion != null && majorVersion == 18 && guardCount != null && guardCount >= 1;
            return new PostgresAuthorityStatus(healthy, majorVersion != null ? majorVersion : 0, guardCount != null ? guardCount : 0);
        } catch (Exception e) {
            return new PostgresAuthorityStatus(false, 0, 0);
        }
    }

    public record PostgresAuthorityStatus(boolean authoritative, int majorVersion, long guardRecordsCount) {}
}
