package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class ContextualLoginControllerIT {

  private MockMvc mockMvc;

  @BeforeEach
  void configureMockMvc() {
    mockMvc = standaloneSetup(new ContextualLoginController()).build();
  }

  @Test
  void storesValidatedTargetAndStartsTheFixedOidcAuthorizationEndpoint() throws Exception {
    String target = "/odds/mercados/11111111-1111-4111-8111-111111111111?selection=AWAY#bilhete";

    MvcResult result =
        mockMvc
            .perform(get("/login/start").queryParam("returnTo", target))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/oauth2/authorization/keycloak"))
            .andReturn();

    HttpSession session = result.getRequest().getSession(false);
    assertThat(session).isNotNull();
    assertThat(session.getAttribute(SessionLifecycleService.CONTEXTUAL_LOGIN_TARGET_ATTRIBUTE))
        .isEqualTo(target);
  }

  @Test
  void rejectsUnsafeTargetBeforeCreatingASession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/login/start").queryParam("returnTo", "https://evil.invalid/steal"))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(result.getRequest().getSession(false)).isNull();
    assertThat(result.getResponse().getHeader("Location")).isNull();
  }
}
