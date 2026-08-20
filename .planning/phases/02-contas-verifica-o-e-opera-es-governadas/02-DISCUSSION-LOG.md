# Fase 2: Contas, Verificação e Operações Governadas — Registro da Discussão

> **Somente trilha de auditoria.** Não usar como entrada para agentes de pesquisa, planejamento ou execução.
> As decisões estão em `02-CONTEXT.md` — este registro preserva as alternativas consideradas e o que foi recusado.

**Data:** 2026-08-19 / 2026-08-20
**Fase:** 2-Contas, Verificação e Operações Governadas
**Áreas discutidas:** escopo da verificação UFU, normalização e duplicidade da matrícula, chave HMAC, cadastro/e-mail/recuperação, perfil público e apelido, papéis operacionais, MFA e step-up

---

## Escopo da verificação UFU

### Validade da verificação

| Opção | Selecionada |
|---|---|
| Prazo fixo de 12 meses | |
| Amarrada ao período letivo | |
| Prazo fixo, valor configurável | |
| Sem expiração no MVP | |

**Resposta do responsável (texto livre):** *"Bom estava pensando para um mvp sem isso de validar a conta, e deixar isso como uma feature para o futuro"*, esclarecido depois como *"Apenas olhariamos se a matricula já está registrada no sistema por enquanto"*.

**Notas:** a resposta não correspondia a nenhuma opção. Foram apresentadas três leituras — remover só a expiração, remover a verificação inteira, ou manter com expiração desligada — e o responsável esclareceu que a verificação documental sai e resta apenas a checagem de unicidade.

### Destino dos requisitos que saem

| Opção | Descrição | Selecionada |
|---|---|---|
| Nova fase antes da Fase 5 | Fase própria agendada antes de VERI-09 passar a valer para apostas | |
| Para a v2, fora do MVP inteiro | Comprovante sai do MVP; risco de registro indevido aceito em produção | ✓ |
| Manter o comprovante na Fase 2 | Recuo da redução; só a expiração seria simplificada | |

**Notas:** o risco foi apresentado antes da escolha — sem comprovante, "verificado" passa a significar "digitou um número que ninguém tinha digitado antes", e como o número original não é conservado, a pessoa legítima e um impostor produzem o mesmo HMAC, de modo que nenhum dado armazenado distingue os dois. O responsável decidiu com o risco à vista.

**Correção feita durante a análise:** `PRIV-02` não foi movido para a v2. `SPRT-04` (Fase 3) exige comprovante para incluir atleta no elenco e diz textualmente que o número da matrícula isolado não é prova suficiente — então comprovantes continuam existindo no MVP, de atleta. `PRIV-02` foi **reatribuído à Fase 3**, o que também preserva `DATA-06` e o armazenamento S3 da Fase 1.

**Assimetria registrada:** para atleta, matrícula sozinha não basta como prova; para conta de usuário, no MVP, basta.

---

## Normalização e duplicidade da matrícula

### Formato real

| Opção | Selecionada |
|---|---|
| Só dígitos | |
| Dígitos e letras | ✓ |
| Varia por curso ou por época | |
| Não tenho certeza | |

### Regra de normalização

| Opção | Selecionada |
|---|---|
| Normalizar e validar o formato | ✓ |
| Só normalizar, sem validar formato | |
| Só validar, sem normalizar | |

### Colisão de matrícula

| Opção | Selecionada |
|---|---|
| Recusa explícita com limite de tentativas | ✓ |
| Recusa explícita, sem limite | |
| Recusa genérica | |

**Notas:** apresentada a tensão entre o critério de sucesso do roadmap (recusa "compreensível") e o oráculo de enumeração que uma mensagem informativa cria. Atenuante registrado: cada conta registra no máximo uma matrícula, então sondagem em massa exige criar muitas contas com e-mail confirmado.

### Mutabilidade

| Opção | Selecionada |
|---|---|
| Imutável; só administrador corrige, auditado | ✓ |
| Editável numa janela curta, depois imutável | |
| Imutável, sem exceção alguma | |

---

## Chave HMAC: versão, rotação e comprometimento

### Onde a chave vive

| Opção | Selecionada |
|---|---|
| Variável de ambiente `BETUFU_*`, fora do banco | ✓ |
| Serviço de gestão de segredos (Vault/KMS) | |
| Tabela de configuração no PostgreSQL | |

**Notas:** Vault/KMS foi recusado por exigir escolher provedor de nuvem no meio da Fase 2, decisão que `PROJECT.md` mantém em aberto. A tabela no PostgreSQL foi listada por honestidade e não como recomendação — anularia a proteção inteira.

### Versionamento

| Opção | Selecionada |
|---|---|
| Coluna dedicada ao lado do HMAC | ✓ |
| Prefixo dentro do próprio valor | |
| Não versionar | |

### Rotação planejada

| Opção | Selecionada |
|---|---|
| Chaves antigas seguem ativas só para comparação | ✓ |
| Rotação exige que todos reinformem | |
| Sem rotação no MVP | |

**Notas:** apresentado durante a discussão que, sem manter as chaves antigas para comparação, `VERI-07` cairia silenciosamente no dia da rotação — a mesma matrícula sob duas versões produz valores diferentes e o banco não sabe que são a mesma pessoa.

### Comprometimento

| Opção | Selecionada |
|---|---|
| Rotacionar, reinformar e expurgar os HMACs antigos | ✓ |
| Rotacionar e manter a versão comprometida para comparação | |
| Definir o procedimento na fase de implantação | |

---

## Cadastro, confirmação de e-mail e recuperação

### E-mail não confirmado

| Opção | Selecionada |
|---|---|
| Não — Keycloak barra até confirmar | ✓ |
| Sim, com ações limitadas pela BETUFU | |
| Sim, sem limitação alguma no MVP | |

### Perfil no primeiro acesso

| Opção | Selecionada |
|---|---|
| Obrigatório antes de navegar | ✓ |
| Apelido gerado automaticamente, editável depois | |
| Opcional, pedido só quando necessário | |

### Redefinição de senha e sessões abertas

| Opção | Selecionada |
|---|---|
| Sim — encerra todas as sessões da conta | ✓ |
| Não — sessões seguem até o timeout | |
| Só encerra na próxima ida ao Keycloak | |

### Identificador de login

| Opção | Selecionada |
|---|---|
| O e-mail | ✓ |
| Nome de usuário próprio, separado do e-mail | |
| E-mail ou nome de usuário, os dois aceitos | |

---

## Perfil público e apelido

### Unicidade

| Opção | Selecionada |
|---|---|
| Único, ignorando maiúsculas | |
| Não precisa ser único | ✓ |
| Único, respeitando maiúsculas | |

**Notas:** a recomendação era a unicidade sem sensibilidade a caixa. O responsável optou por apelido livre, aceitando homônimos legítimos e falsificação de identidade sem esforço.

### Distinção entre homônimos

| Opção | Selecionada |
|---|---|
| Discriminador curto colado ao apelido | |
| Só o apelido; a distinção vive no perfil | ✓ |
| Apelido mais um dado de contexto | |

**Notas:** pergunta criada pela decisão anterior. A recomendação era o discriminador visível; o responsável aceitou manter a ambiguidade nas listas.

### Caracteres e tamanho

| Opção | Selecionada |
|---|---|
| ASCII estrito, 3 a 20 caracteres | ✓ |
| Latino com acentos, normalizado | |
| Permissivo, só bloqueando o inválido | |

### Equipe favorita antes da Fase 3

| Opção | Selecionada |
|---|---|
| Modelar agora, nulo, seletor aparece na Fase 3 | ✓ |
| Adiar o campo inteiro para a Fase 3 | |
| Texto livre agora, convertido depois | |

---

## Papéis operacionais e escopo

### Quais papéis modelar

| Opção | Selecionada |
|---|---|
| Os três: geral, competição e moderador | ✓ |
| Só o administrador geral | |
| Um mecanismo genérico de papéis | |

**Notas:** o mecanismo genérico foi recusado com razão explícita — papel definido por dado escapa da análise estática, do ArchUnit e dos testes de autorização.

### Bootstrap do primeiro administrador

| Opção | Selecionada |
|---|---|
| Semente por configuração de ambiente, idempotente | ✓ |
| Migration Flyway com o admin inicial fixo | |
| Quem tem `TECHNICAL` concede o primeiro papel | |

**Notas:** a migration foi recusada por gravar identidade real num repositório que pode se tornar público (`PRIV-03`); o bootstrap por `TECHNICAL` foi recusado por desfazer a separação que `D-20` estabeleceu entre acesso técnico e autoridade de domínio.

### Limites sobre os próprios papéis

| Opção | Selecionada |
|---|---|
| Não concede a si mesmo e não pode ser o último a sair | ✓ |
| Concessão administrativa exige dois administradores distintos | |
| Sem limites, tudo auditado | |

**Notas:** a exigência de dois administradores — padrão que o projeto já usa em override de odds e confirmação de resultado — foi recusada por conflitar com a semente de um único administrador, que exigiria uma exceção de bootstrap.

### Concessão/revogação e sessões ativas

| Opção | Selecionada |
|---|---|
| Encerra todas as sessões da pessoa afetada | ✓ |
| Reavaliar a autoridade a cada requisição | |
| Encerrar só quando envolver papel administrativo | |

---

## MFA e step-up

### Conflito D-07 (Fase 1) × IDEN-05 (Fase 2)

| Opção | Selecionada |
|---|---|
| Exigir MFA recente, sem fluxo de step-up dedicado | |
| Step-up completo: D-07 cede por inteiro | |
| Reduzir IDEN-05: D-07 prevalece | |

**Resposta do responsável (texto livre):** *"o MFA vai ser opiconal no MVP, depois podemos ver se precisamos aplicar obrigatoriedade"*.

**Notas:** a resposta foi além das três opções — retirou a obrigatoriedade de MFA, que era regra transversal de arquitetura no `PROJECT.md`, não só cláusula do `IDEN-05`. Foi levantado o mapa completo das menções a MFA e apresentado o risco: com MFA opcional, a governança da plataforma passa a depender apenas de senha.

### Precisões pedidas antes de emendar

| Pergunta | Resposta |
|---|---|
| O que "MFA opcional" significa? | Disponível para quem quiser, nunca exigido |
| Vale só para a Fase 2 ou também para `ODDS-12` da Fase 4? | Vale para todo o MVP, inclusive `ODDS-12` |

### Publicação de `mfaAuthenticated`

| Opção | Selecionada |
|---|---|
| Não — fecha a pendência mantendo a ausência | ✓ |
| Sim — publicar como informativo | |
| Remover o campo do DTO | |

---

## Discrição dos agentes posteriores

- Frequência de troca do apelido — oferecida como pergunta adicional e declinada pelo responsável.
- Padrão exato da matrícula UFU, a confirmar na pesquisa.
- Algoritmo e tamanho da chave HMAC; formato de armazenamento do digest.
- Valores numéricos do limite de tentativas e da janela de contagem.
- Como a semente do administrador identifica a pessoa.
- Mecanismo de integração do gatilho `SECURITY`.
- Códigos de erro estáveis e textos, respeitando `D-14` a `D-17`.
- Conteúdo do painel operacional (`OPER-06`), que pertence ao `UI-SPEC.md`.

## Ideias adiadas

- Verificação documental do vínculo UFU — movida para a v2 de `REQUIREMENTS.md`.
- Obrigatoriedade de MFA — reavaliação futura.
- Step-up de MFA por operação sensível — sem consumidor enquanto o MFA não condicionar comandos.
- Discriminador visível para homônimos — recusado; primeira coisa a reconsiderar se a ambiguidade incomodar.
- Prazo de validade do vínculo UFU.

## Emendas de escopo aplicadas durante esta discussão

Duas, ambas aprovadas separadamente pelo responsável e aplicadas por script com verificação exata de alvo:

1. **2026-08-19 — "verificação documental → v2":** 43 edições em `REQUIREMENTS.md`, `ROADMAP.md` e `PROJECT.md`. Fase 2 de 27 para 18 requisitos; v1 de 145 para 137; `PRIV-02` reatribuído à Fase 3.
2. **2026-08-20 — "MFA opcional no MVP":** 10 edições nos mesmos três arquivos, alcançando `IDEN-05`, `ODDS-12`, os critérios 5 das Fases 2 e 4, e as regras transversais do `PROJECT.md`.

Ambas verificadas com `gsd-tools query roadmap validate` sem avisos, conjuntos de requisitos idênticos entre declaração e rastreabilidade, e finais de linha preservados por arquivo.
