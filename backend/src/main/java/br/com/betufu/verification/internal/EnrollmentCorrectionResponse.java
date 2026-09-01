package br.com.betufu.verification.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Confirmação da correção administrativa de matrícula (D-32).
 *
 * <p>Allowlist deliberada de quatro campos. O contrato devolve apenas <strong>referência opaca de
 * pessoa e estado resultante</strong>; jamais o número da matrícula, o digest HMAC ou a versão da
 * chave — nem do vínculo novo, nem do antigo.
 *
 * <p>O {@code 02-UI-SPEC.md} S6 é explícito: <em>o número anterior não é exibido, porque a
 * plataforma não o conserva</em>. Não existe campo que possa reexibi-lo, nem mascarado, nem
 * parcial. Acrescentar aqui qualquer componente cujo nome ou tipo carregue vocabulário de matrícula
 * ou digest deixa o {@code PrivacyArchitectureTest} vermelho.
 *
 * @param targetIdentityId identificador público da conta corrigida
 * @param status estado do vínculo resultante ({@code ATIVO})
 * @param correctedAt instante do vínculo resultante, atribuído pelo PostgreSQL
 * @param previousBindingReleased se havia vínculo anterior e ele foi liberado; {@code false}
 *     identifica o registro administrativo em conta sem vínculo e a correção inócua
 */
public record EnrollmentCorrectionResponse(
    UUID targetIdentityId, String status, Instant correctedAt, boolean previousBindingReleased) {}
