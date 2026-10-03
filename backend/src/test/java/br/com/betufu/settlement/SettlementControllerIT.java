package br.com.betufu.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.betufu.platform.api.PhaseSixHttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;

class SettlementControllerIT extends PhaseSixHttpTestSupport {

  @Test
  void ownerHistoryIsScopedAndAnonymousRejected() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/settlements").accept("application/json"))
        .andExpect(status().isUnauthorized());
    when(settlements.findHistoryByIdentity(owner)).thenReturn(List.of());
    String body =
        mockMvc
            .perform(get("/api/v1/me/settlements").with(user()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    verify(settlements).findHistoryByIdentity(owner);
    assertThat(body).doesNotContain(owner.toString(), ISSUER, SUBJECT);
  }

  @Test
  void operationsRequireGeneralAdministrator() throws Exception {
    mockMvc
        .perform(get("/api/v1/operations/settlements/candidates").with(user()))
        .andExpect(status().isForbidden());
    verifyNoInteractions(settlements);
  }

  @Test
  void staleExactFactIs409AndNeverProcessed() throws Exception {
    when(identities.hasGlobalRole(owner, "ADMINISTRADOR_GERAL")).thenReturn(true);
    when(processes.authorize(any(), any(), any(), eq(owner), anyString()))
        .thenThrow(new IllegalStateException("private actor and details"));
    mockMvc
        .perform(
            post("/api/v1/operations/settlements/events/" + UUID.randomUUID() + "/authorize")
                .with(user())
                .with(csrf())
                .header("Idempotency-Key", "http-1")
                .contentType("application/json")
                .content("{\"expectedOutcomeFactId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BETUFU-409-STALE_VERSION"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private actor"))));
    verify(processes, never()).process(any());
  }

  @Test
  void factIsRequiredAndCsrfCannotBeBypassed() throws Exception {
    when(identities.hasGlobalRole(owner, "ADMINISTRADOR_GERAL")).thenReturn(true);
    String path = "/api/v1/operations/settlements/events/" + UUID.randomUUID() + "/authorize";
    mockMvc
        .perform(
            post(path)
                .with(user())
                .header("Idempotency-Key", "http-2")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post(path)
                .with(user())
                .with(csrf())
                .header("Idempotency-Key", "http-2")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(processes);
  }

  @Test
  void authorizationReportsRealPartialProgress() throws Exception {
    UUID event = UUID.randomUUID(), fact = UUID.randomUUID(), version = UUID.randomUUID();
    UUID auth = UUID.randomUUID(), settlementCase = UUID.randomUUID();
    when(identities.hasGlobalRole(owner, "ADMINISTRADOR_GERAL")).thenReturn(true);
    when(processes.authorize(event, fact, version, owner, "http-3")).thenReturn(auth);
    when(processes.process(auth)).thenReturn(settlementCase);
    when(settlements.findCase(settlementCase))
        .thenReturn(
            Optional.of(
                new br.com.betufu.settlement.SettlementReadPort.CaseProgress(
                    settlementCase,
                    br.com.betufu.settlement.SettlementReadPort.ProcessingStatus.RUNNING,
                    4,
                    2)));
    mockMvc
        .perform(
            post("/api/v1/operations/settlements/events/" + event + "/authorize")
                .with(user())
                .with(csrf())
                .header("Idempotency-Key", "http-3")
                .contentType("application/json")
                .content(
                    "{\"expectedOutcomeFactId\":\""
                        + fact
                        + "\",\"expectedConfirmedVersionId\":\""
                        + version
                        + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.caseProgress.status").value("RUNNING"))
        .andExpect(jsonPath("$.caseProgress.completedUnits").value(2));
  }
}
