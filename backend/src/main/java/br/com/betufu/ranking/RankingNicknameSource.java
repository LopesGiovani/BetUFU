package br.com.betufu.ranking;

import java.util.Optional;
import java.util.UUID;

/** Publicly chosen nickname, without account or identity-provider attributes. */
public interface RankingNicknameSource {
  Optional<String> findNickname(UUID identityId);
}
