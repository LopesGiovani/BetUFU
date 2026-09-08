package br.com.betufu.odds.internal.publicview;

import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsView;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
@RequestMapping(path = "/api/v1/public/odds", produces = MediaType.APPLICATION_JSON_VALUE)
public class OddsPublicController {
  private final OddsPublicViewService service;

  public OddsPublicController(OddsPublicViewService service) {
    this.service = service;
  }

  @GetMapping("/markets/{marketId}")
  public PublicOddsView get(@PathVariable UUID marketId) {
    return service.get(marketId);
  }
}
