package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.betufu.platform.api.PhaseSixHttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;

class RankingControllerIT extends PhaseSixHttpTestSupport {

  @Test
  void publicRankingPreservesInactiveHistoryWithoutIdentity() throws Exception {
    when(rankings.findLifetimeRanking())
        .thenReturn(
            List.of(
                new br.com.betufu.ranking.RankingModels.Entry(
                    1, "torcedor", 3, 1, new java.math.BigDecimal("0.75"), false)));
    String body =
        mockMvc
            .perform(get("/api/v1/public/ranking/lifetime"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].verificationActive").value(false))
            .andExpect(jsonPath("$.entries[0].wins").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("identityId", "email", "enrollmentId", ISSUER);
  }
}
