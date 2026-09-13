# Phase 5: Créditos Virtuais e Palpites Protegidos - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `05-CONTEXT.md`; this log preserves the alternatives considered.

**Date:** 2026-09-12
**Phase:** 5 - Créditos Virtuais e Palpites Protegidos
**Areas discussed:** concessão e limites, precisão e retorno, conflito
administrativo tardio, bilhete e comprovante

> Este registro formaliza escolhas feitas interativamente. Valores que foram
> consolidados em conjunto só aparecem como aprovados porque o responsável
> confirmou explicitamente o conjunto antes de autorizar a documentação.

---

## Concessão inicial

| Option | Description | Selected |
|--------|-------------|----------|
| 100 créditos | Número pequeno e simples, com pouca granularidade | |
| 1.000 créditos | Boa granularidade sem números inflados | ✓ |
| 10.000 créditos | Alta granularidade, mas escala visual inflada | |

**User's choice:** concessão inicial única de `1.000,00` créditos.

**Notes:** A revalidação não concede novamente. A concessão é auditável,
idempotente e não representa dinheiro.

---

## Limites e precisão

| Option | Description | Selected |
|--------|-------------|----------|
| Máximo fixo de 100 | Limita cada palpite a 10% da concessão inicial | |
| Máximo fixo de 250 | Permite comprometer 25% da concessão inicial | |
| Todo o saldo disponível | Não aplica teto fixo menor que o saldo; permite all-in | ✓ |
| Mínimo de 10,00 | Piso consolidado e confirmado no conjunto econômico | ✓ |
| Escala de duas casas | Permite apostar exatamente saldos com centavos | ✓ |

**User's choice:** mínimo de `10,00`, máximo igual ao saldo disponível e
escala de duas casas decimais.

**Notes:** Mínimo e precisão foram ratificados na confirmação explícita do
conjunto econômico. A aposta continua sujeita à revalidação autoritativa do
saldo no servidor.

---

## Fórmula do retorno

| Option | Description | Selected |
|--------|-------------|----------|
| Retorno decimal padrão | `valor × odd`, incluindo a aposta original | ✓ |
| Somente lucro | Creditar apenas `valor × (odd - 1)` e consumir a aposta original | |
| Fórmula própria | Usar uma relação diferente das odds decimais publicadas | |

**User's choice:** retorno total igual a `valor apostado × odd aceita`, com a
aposta original incluída.

**Notes:** O bilhete pode mostrar também o lucro líquido. A Fase 5 congela a
regra e o retorno potencial; o pagamento efetivo pertence à Fase 6.

---

## Arredondamento

| Option | Description | Selected |
|--------|-------------|----------|
| HALF_UP | Arredonda para o valor mais próximo e sobe no empate | ✓ |
| Sempre para baixo | Evita criar fração adicional, mas sempre desfavorece o usuário | |
| HALF_EVEN | Reduz viés estatístico, porém é menos intuitivo | |

**User's choice:** escala 2 com `HALF_UP`.

**Notes:** A escolha mantém consistência com as odds da Fase 4 e será parte da
regra versionada.

---

## Conflito administrativo tardio

| Option | Description | Selected |
|--------|-------------|----------|
| Papel concedido depois preserva o palpite | Bloqueia somente novas apostas a partir da vigência | ✓ |
| Papel concedido depois anula o palpite | Aplica retroativamente a nova restrição | |
| Papel concedido depois suspende o palpite | Exige análise administrativa antes de prosseguir | |
| Conflito preexistente: anular e devolver | Compensa o débito sem apagar o histórico | ✓ |
| Conflito preexistente: manter | Preserva uma aposta que violava regra vigente | |
| Conflito preexistente: análise manual | Suspende a decisão até revisão operacional | |

**User's choice:** concessão posterior não tem efeito retroativo. Quando o
conflito já existia na aceitação e é comprovado depois, anular o palpite e
devolver os créditos por lançamentos compensatórios.

**Notes:** A mensagem ao usuário deve ser genérica e acionável, confirmar a
devolução e trazer código de referência, sem revelar papel ou escopo. Casos
pós-liquidação com créditos já gastos ficam para a política da Fase 6.

---

## Bilhete conforme elegibilidade

| Option | Description | Selected |
|--------|-------------|----------|
| Ação orientada por estado | Login, registro de matrícula, formulário elegível ou inelegibilidade genérica | ✓ |
| Ocultar o bilhete | Não mostra a capacidade a quem ainda não pode apostar | |
| Formulário sempre desabilitado | Exibe o mesmo bloqueio genérico para todos | |

**User's choice:** integrar o bilhete à página pública de odds e orientar cada
estado com sua próxima ação, sem transferir autorização para o frontend.

**Notes:** A mudança de versão da odd invalida o envio antigo e exige
reconfirmação explícita conforme `ODDS-14`; a apresentação visual exata será
decidida no contrato de UI.

---

## Comprovante do próprio palpite

| Option | Description | Selected |
|--------|-------------|----------|
| Cartão persistente no mercado | Substitui o formulário e reaparece após reload | ✓ |
| Confirmação temporária | Mostra sucesso apenas imediatamente após o envio | |
| Página separada | Redireciona para um comprovante dedicado | |

**User's choice:** cartão persistente na própria página do mercado.

**Notes:** O cartão mostra somente o palpite da pessoa autenticada. Histórico
completo e estados de liquidação pertencem à Fase 6; dados de terceiros
permanecem ocultos antes do fechamento.

## the agent's Discretion

- Estrutura interna de módulos, tabelas, locks, receipts e fingerprints, desde
  que preserve as garantias aprovadas e os limites Spring Modulith.
- Códigos de erro e textos finais compatíveis com Problem Details e privacidade.
- Composição visual detalhada subordinada ao futuro `05-UI-SPEC.md`.

## Deferred Ideas

- Liquidação, pagamentos, ranking e histórico completo na Fase 6.
- Correção depois de pagamento já gasto e aprovação de ajuste direto no ledger.
- Visibilidade agregada depois do fechamento do mercado.
- Qualquer compra, transferência, saque, recarga, aposta ao vivo ou múltipla.
