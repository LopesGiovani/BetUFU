package br.com.betufu.platform.operations;

/**
 * Contrato consumidor somente leitura, pertencente ao módulo platform, para consulta agregada da
 * contagem de contas sem vínculo de matrícula ativa (OPER-06, S4 do 02-UI-SPEC.md).
 *
 * <p>Para proteger a privacidade e impedir que o agregado funcione como oráculo de enumeração
 * (T-02-10-01), este contrato declara exclusivamente um método que devolve um inteiro primitivo,
 * sem coleções, listas ou identificadores de identidade.
 *
 * <p>A assinatura usa exclusivamente tipos neutros e nenhum tipo do pacote {@code
 * br.com.betufu.verification} para preservar a direção modular (verification -> platform).
 */
public interface EnrollmentAggregateLookup {

  /**
   * Devolve a contagem agregada de contas cadastradas na plataforma que não possuem matrícula UFU
   * ativa vinculada.
   *
   * @return número total de contas sem matrícula ativa
   */
  int countAccountsWithoutActiveEnrollment();
}
