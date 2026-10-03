package br.com.betufu.ranking.internal;

import br.com.betufu.ranking.RankingModels.Entry;
import br.com.betufu.ranking.RankingQueryPort;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class RankingController {
  static final String PUBLIC_RANKING_PATH = "/api/v1/public/ranking/lifetime";

  private final RankingQueryPort ranking;

  public RankingController(RankingQueryPort ranking) {
    this.ranking = ranking;
  }

  @GetMapping(path = PUBLIC_RANKING_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RankingPage> lifetime() {
    return ResponseEntity.ok(new RankingPage(ranking.findLifetimeRanking()));
  }

  public record RankingPage(List<Entry> entries) {
    public RankingPage {
      entries = entries == null ? List.of() : List.copyOf(entries);
    }
  }
}
