package br.com.betufu.ledger;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.betufu.platform.api.PhaseSixHttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;

class CreditCorrectionControllerIT extends PhaseSixHttpTestSupport {

  @Test
  void correctionsAreOwnerScoped() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/credit-corrections").accept("application/json"))
        .andExpect(status().isUnauthorized());
    when(corrections.listForOwner(owner)).thenReturn(List.of());
    mockMvc
        .perform(get("/api/v1/me/credit-corrections").with(user()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    verify(corrections).listForOwner(owner);
  }

  @Test
  void makerCannotApproveOwnCorrectionAndErrorIsSafe() throws Exception {
    UUID correction = UUID.randomUUID();
    when(corrections.approve(owner, correction))
        .thenThrow(new SecurityException("private maker identity"));
    mockMvc
        .perform(
            post("/api/v1/operations/credit-corrections/" + correction + "/approve")
                .with(user())
                .with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"));
    mockMvc
        .perform(
            post("/api/v1/operations/credit-corrections/" + correction + "/approve").with(user()))
        .andExpect(status().isForbidden());
    verify(corrections, times(1)).approve(owner, correction);
  }
}
