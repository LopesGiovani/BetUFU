package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.betufu.platform.api.PhaseSixHttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;

class AchievementControllerIT extends PhaseSixHttpTestSupport {

  @Test
  void achievementsAreOwnerOnlyAndRevokedAwardsStayVisible() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/achievements").accept("application/json"))
        .andExpect(status().isUnauthorized());
    when(achievements.findByIdentity(owner))
        .thenReturn(
            List.of(
                new br.com.betufu.ranking.AchievementQueryPort.Achievement(
                    UUID.randomUUID(),
                    "streak",
                    "Sequencia",
                    "Descricao",
                    "Tres acertos",
                    "v1",
                    null,
                    br.com.betufu.ranking.AchievementQueryPort.AwardState.REVOKED,
                    java.time.Instant.parse("2026-10-01T00:00:00Z"),
                    java.time.Instant.parse("2026-10-02T00:00:00Z"),
                    "CORRECTED_SETTLEMENT_FACT")));
    String body =
        mockMvc
            .perform(get("/api/v1/me/achievements").with(user()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].state").value("REVOKED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    verify(achievements).findByIdentity(owner);
    assertThat(body).doesNotContain(owner.toString(), ISSUER, SUBJECT, "email", "enrollment");
  }
}
