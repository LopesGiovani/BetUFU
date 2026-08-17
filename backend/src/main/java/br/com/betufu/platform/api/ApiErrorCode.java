package br.com.betufu.platform.api;

/**
 * Stable error codes for BETUFU API responses.
 *
 * <p>Each code is a fixed string that the frontend can use to map to user-facing messages
 * and actions. Codes must never be renamed or removed once published — they become part
 * of the API contract per D-14.
 *
 * <p>{@code title} and {@code detail} are the RFC 9457 standard fields, in Portuguese.
 * {@code action} is the BETUFU extension that tells the user what to do next.
 */
public enum ApiErrorCode {

    VALIDATION_FAILED(
            "BETUFU-400-VALIDATION",
            "Erro de validação",
            "Um ou mais campos estão inválidos.",
            "Corrija os campos indicados e tente novamente."),

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

    UNSUPPORTED_MEDIA_TYPE(
            "BETUFU-415-MEDIA_TYPE",
            "Formato não aceito",
            "O formato enviado não é aceito.",
            "Verifique o cabeçalho Content-Type da requisição."),

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
     * <p>Unknown statuses fall back to {@link #INTERNAL_ERROR} — a missing mapping
     * must never produce a null code in the response.
     */
    public static ApiErrorCode fromStatus(int status) {
        return switch (status) {
            case 400 -> VALIDATION_FAILED;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> INTERNAL_ERROR;
        };
    }
}
