package br.com.betufu.odds.internal.publicview;

import br.com.betufu.odds.internal.publicview.OddsPublicViewModels.PublicOddsView;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class OddsPublicViewService {
  private final OddsPublicViewRepository repository;

  public OddsPublicViewService(OddsPublicViewRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public PublicOddsView get(UUID marketId) {
    if (marketId == null) throw notFound();
    return repository.find(marketId).orElseThrow(OddsPublicViewService::notFound);
  }

  private static ApiProblemException notFound() {
    return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
  }
}
