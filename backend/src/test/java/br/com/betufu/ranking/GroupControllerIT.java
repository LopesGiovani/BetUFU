package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.betufu.platform.api.PhaseSixHttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;

class GroupControllerIT extends PhaseSixHttpTestSupport {

  @Test
  void privateGroupsRejectAnonymousAndScopeTheOwner() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/groups").accept("application/json"))
        .andExpect(status().isUnauthorized());
    when(groups.findMine(ISSUER, SUBJECT)).thenReturn(List.of());
    mockMvc.perform(get("/api/v1/me/groups").with(user())).andExpect(status().isOk());
    verify(groups).findMine(ISSUER, SUBJECT);
  }

  @Test
  void nonmemberGetsSameSanitized404AsMissingGroup() throws Exception {
    UUID group = UUID.randomUUID();
    when(groups.findGroup(ISSUER, SUBJECT, group))
        .thenThrow(new SecurityException("secret group roster"));
    String rejected =
        mockMvc
            .perform(get("/api/v1/me/groups/" + group).with(user()))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    doReturn(Optional.empty()).when(groups).findGroup(ISSUER, SUBJECT, group);
    String missing =
        mockMvc
            .perform(get("/api/v1/me/groups/" + group).with(user()))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(rejected).isEqualTo(missing).doesNotContain("secret", "roster");
  }

  @Test
  void invalidInviteIsSanitizedAndMutationsRequireCsrf() throws Exception {
    when(groupCommands.join(ISSUER, SUBJECT, "bad"))
        .thenThrow(new NoSuchElementException("invite hash"));
    mockMvc
        .perform(
            post("/api/v1/me/groups/join")
                .with(user())
                .with(csrf())
                .contentType("application/json")
                .content("{\"code\":\"bad\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BETUFU-404-NOT_FOUND"));
    mockMvc
        .perform(
            post("/api/v1/me/groups")
                .with(user())
                .contentType("application/json")
                .content("{\"name\":\"amigos\"}"))
        .andExpect(status().isForbidden());
    verify(groupCommands, never()).create(anyString(), anyString(), anyString());
  }
}
