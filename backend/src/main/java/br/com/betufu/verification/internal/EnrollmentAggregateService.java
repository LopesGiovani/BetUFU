package br.com.betufu.verification.internal;

import br.com.betufu.platform.operations.EnrollmentAggregateLookup;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação do contrato consumidor {@link EnrollmentAggregateLookup} no módulo de verificação.
 *
 * <p>Executa consulta agregada segura no banco de dados para contabilizar identidades cadastradas
 * sem vínculo de matrícula ativa, preservando a seta arquitetural {@code verification -> platform}
 * e impedindo a formação de ciclos modulares.
 *
 * <p>Devolve exclusivamente um inteiro agregado primitivo, sem carregar listas nem expor
 * identificadores de pessoa (T-02-10-01).
 */
@Service
@Profile("runtime | integrated")
public class EnrollmentAggregateService implements EnrollmentAggregateLookup {

  private final JdbcTemplate jdbcTemplate;

  public EnrollmentAggregateService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  @Override
  @Transactional(readOnly = true)
  public int countAccountsWithoutActiveEnrollment() {
    String sql =
        """
                SELECT count(*)
                FROM public.local_identity i
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM public.ufu_enrollment e
                    WHERE e.identity_id = i.identity_id
                      AND e.status = 'ATIVO'
                )
                """;
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
    return count != null ? count : 0;
  }
}
