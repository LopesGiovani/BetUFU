package br.com.betufu.platform.diagnostic;

import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expoe a projecao diagnostica do Plano 01-13, e somente em ambientes autorizados.
 *
 * <p><strong>Ausencia material em producao, per D-02.</strong> O {@link Profile} restringe o bean a
 * {@code dev} e {@code homolog}. Em {@code prod} nao existe bean nem mapping — nao e um 403, e
 * ausencia. Um endpoint que responde 403 revela que existe; um que nao esta registrado nao revela
 * nada, e nao pode ser alcancado por defeito de configuracao de autorizacao. O
 * {@code DiagnosticControllerIT} prova isso inspecionando o contexto, nao o status HTTP, pelo mesmo
 * motivo que o Plano 01-21 provou a ausencia do tracer inspecionando o bundle em vez de navegar.
 *
 * <p><strong>Autorizacao.</strong> A exigencia de {@code ROLE_TECHNICAL} vive na cadeia de
 * seguranca unica, em {@code SecurityConfiguration}, com um matcher anterior ao de
 * {@code /api/v1/**}. Uma segunda {@code SecurityFilterChain} teria precedencia dependente de
 * ordem de bean, que nao e auditavel por leitura. A autoridade so pode vir do
 * {@code OidcTechnicalAuthorityMapper} da Task 1, que a concede exclusivamente pela claim
 * aprovada.
 *
 * <p><strong>Nao vazamento.</strong> O controller devolve o {@link DiagnosticResponse} do 01-13 sem
 * acrescentar campo, cabecalho ou detalhe de excecao. A prova de nao vazamento feita na camada de
 * projecao continua valida na serializacao HTTP porque nada e reexposto aqui.
 */
@RestController
@Profile("dev | homolog")
public class DiagnosticController {

    static final String DIAGNOSTIC_PATH = "/api/v1/diagnostic";

    private final DiagnosticService diagnosticService;
    private final PlatformDependencyProbes dependencyProbes;

    public DiagnosticController(
            DiagnosticService diagnosticService,
            PlatformDependencyProbes dependencyProbes) {
        this.diagnosticService = Objects.requireNonNull(
                diagnosticService, "diagnosticService must not be null");
        this.dependencyProbes = Objects.requireNonNull(
                dependencyProbes, "dependencyProbes must not be null");
    }

    @GetMapping(path = DIAGNOSTIC_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DiagnosticResponse> getDiagnostic() {
        return ResponseEntity.ok(diagnosticService.aggregate(dependencyProbes.probeAll()));
    }
}
