package br.com.betufu.platform.api;

/**
 * Stable error codes for BETUFU API responses.
 *
 * <p>Each code is a fixed string that the frontend can use to map to user-facing messages and
 * actions. Codes must never be renamed or removed once published — they become part of the API
 * contract per D-14.
 *
 * <p>{@code title} and {@code detail} are the RFC 9457 standard fields, in Portuguese. {@code
 * action} is the BETUFU extension that tells the user what to do next.
 */
public enum ApiErrorCode {
  VALIDATION_FAILED(
      "BETUFU-400-VALIDATION",
      "Erro de validação",
      "Um ou mais campos estão inválidos.",
      "Corrija os campos indicados e tente novamente."),

  UNAUTHORIZED(
      "BETUFU-401-UNAUTHORIZED",
      "Sessão expirada",
      "Sua sessão expirou.",
      "Entre novamente para continuar."),

  FORBIDDEN(
      "BETUFU-403-FORBIDDEN",
      "Sem autorização",
      "Você não tem acesso a esta área.",
      "Retorne à página inicial."),

  SELF_GRANT_BLOCKED(
      "BETUFU-403-SELF_GRANT_BLOCKED",
      "Autoconcessão bloqueada",
      "Você não pode conceder papéis a si mesmo.",
      "Peça a outro administrador geral."),

  NOT_FOUND(
      "BETUFU-404-NOT_FOUND",
      "Recurso não encontrado",
      "O recurso solicitado não foi encontrado.",
      "Confira o endereço ou atualize a página."),

  METHOD_NOT_ALLOWED(
      "BETUFU-405-METHOD",
      "Método não permitido",
      "O método utilizado não é aceito neste endereço.",
      "Verifique o método HTTP e tente novamente."),

  NOT_ACCEPTABLE(
      "BETUFU-406-NOT_ACCEPTABLE",
      "Formato não disponível",
      "O formato solicitado não está disponível.",
      "Verifique o cabeçalho Accept da requisição."),

  MATRICULA_ALREADY_REGISTERED(
      "BETUFU-409-MATRICULA_DUPLICADA",
      "Matrícula já registrada",
      "Esta matrícula já está registrada em outra conta BETUFU.",
      "Se ela é sua, fale com um administrador geral para pedir a correção."),

  ACCOUNT_ALREADY_ENROLLED(
      "BETUFU-409-CONTA_JA_VINCULADA",
      "Conta já possui matrícula",
      "Sua conta já tem uma matrícula registrada.",
      "Só um administrador geral pode alterá-la."),

  LAST_ADMIN_REVOCATION_BLOCKED(
      "BETUFU-409-LAST_ADMIN",
      "Último administrador geral",
      "Você é o único administrador geral.",
      "Conceda o papel a outra pessoa antes de revogar o seu."),

  CORRECTION_MATRICULA_ALREADY_REGISTERED(
      "BETUFU-409-NOVA_MATRICULA_DUPLICADA",
      "Nova matrícula já registrada",
      "A nova matrícula já está registrada em outra conta.",
      "A correção não foi aplicada."),

  STALE_VERSION(
      "BETUFU-409-STALE_VERSION",
      "Versão desatualizada",
      "O recurso foi alterado desde a sua última leitura.",
      "Atualize os dados antes de tentar novamente."),

  PREDICTION_CONFLICT(
      "BETUFU-409-PREDICTION_CONFLICT",
      "Palpite em conflito",
      "O palpite não pode ser confirmado no estado atual.",
      "Atualize os dados e tente novamente."),

  UNSUPPORTED_MEDIA_TYPE(
      "BETUFU-415-MEDIA_TYPE",
      "Formato não aceito",
      "O formato enviado não é aceito.",
      "Verifique o cabeçalho Content-Type da requisição."),

  NICKNAME_COOLDOWN_ACTIVE(
      "BETUFU-422-NICKNAME_COOLDOWN",
      "Intervalo de troca de apelido",
      "O intervalo mínimo entre trocas de apelido ainda não foi cumprido.",
      "Aguarde o prazo mínimo de 30 dias para realizar uma nova troca."),

  TOO_MANY_ATTEMPTS(
      "BETUFU-429-TOO_MANY_ATTEMPTS",
      "Muitas tentativas",
      "Muitas tentativas de registro.",
      "Aguarde alguns minutos e tente novamente."),

  INTERNAL_ERROR(
      "BETUFU-500-INTERNAL",
      "Erro interno",
      "Ocorreu um erro inesperado.",
      "Tente novamente mais tarde.");

  private final String code;
  private final String title;
  private final String detail;
  private final String action;

  ApiErrorCode(String code, String title, String detail, String action) {
    this.code = code;
    this.title = title;
    this.detail = detail;
    this.action = action;
  }

  public String code() {
    return code;
  }

  public String title() {
    return title;
  }

  public String detail() {
    return detail;
  }

  public String action() {
    return action;
  }

  /**
   * Maps an HTTP status code to the most appropriate error code.
   *
   * <p>Unknown statuses fall back to {@link #INTERNAL_ERROR} — a missing mapping must never produce
   * a null code in the response.
   */
  public static ApiErrorCode fromStatus(int status) {
    return switch (status) {
      case 400 -> VALIDATION_FAILED;
      case 401 -> UNAUTHORIZED;
      case 403 -> FORBIDDEN;
      case 404 -> NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 406 -> NOT_ACCEPTABLE;
      case 409 -> PREDICTION_CONFLICT;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      case 422 -> NICKNAME_COOLDOWN_ACTIVE;
      case 429 -> TOO_MANY_ATTEMPTS;
      default -> INTERNAL_ERROR;
    };
  }
}
