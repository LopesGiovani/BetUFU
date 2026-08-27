# Fase 3: Experiência Esportiva do Piloto — Registro da discussão

> **Somente trilha de auditoria.** Não usar como entrada de planejamento, pesquisa ou execução.
> As decisões canônicas estão em `03-CONTEXT.md`; este arquivo preserva as alternativas consideradas.

**Data:** 2026-08-26
**Fase:** 3 — Experiência Esportiva do Piloto
**Áreas discutidas:** generalização do domínio, modalidades, navegação, administração, chaveamentos, atletas, elencos, fontes, resultados e privacidade

---

## Abrangência do produto

| Opção | Descrição | Selecionada |
|---|---|---|
| Site específico da Olimpíada UFU 2026 | Estruturas e navegação fixas para uma única edição | |
| Plataforma genérica de competições | Várias competições e edições ao longo do ano; 2026 é a primeira operação real | ✓ |

**Escolha do responsável:** plataforma genérica, comparável à organização de campeonatos distintos em sites esportivos.
**Notas:** os dados de 2025 são rascunho e referência, não a edição que será operada.

---

## Modalidades e formatos

| Opção | Descrição | Selecionada |
|---|---|---|
| Suporte detalhado a todas as modalidades | Implementar regras específicas de cada esporte já no MVP | |
| Catálogo completo e operação detalhada de confronto direto | Todos aparecem; somente eventos compatíveis recebem agenda/resultados/chaveamento completo | ✓ |
| Apenas poucas modalidades cadastradas | Omitir as demais do catálogo | |

**Escolha do responsável:** catálogo amplo, com profundidade inicial em confronto direto.
**Notas:** registrar reavaliação futura para enquadrar todas as modalidades; compatibilidade é do formato do evento, não do nome do esporte.

---

## Navegação pública e favorito

| Opção | Descrição | Selecionada |
|---|---|---|
| Agenda primeiro | Hoje, próximos eventos e filtros, sempre identificando competição/edição | ✓ |
| Competição primeiro | Exigir escolha de competição antes de mostrar agenda | |
| Modalidade primeiro | Exigir escolha de esporte antes da agenda | |

**Escolha do responsável:** agenda primeiro.
**Notas:** o favorito representa a atlética/delegação inteira, agregando suas equipes de diferentes modalidades.

---

## Organizações, equipes e elenco

| Opção | Descrição | Selecionada |
|---|---|---|
| Organização permanente, equipes por edição | Atlética preserva identidade; equipes e elencos variam por edição | ✓ |
| Reutilizar equipe e elenco para sempre | Mistura históricos de anos distintos | |
| Recriar a organização por edição | Duplica identidade e seguidores | |

**Escolha do responsável:** organização permanente e equipe/elenco por edição.
**Notas:** escalação de titulares e reservas por partida ficou fora do MVP; existe somente o elenco geral publicado.

---

## Administração escopada

| Opção | Descrição | Selecionada |
|---|---|---|
| Administrador único por competição | Apenas uma pessoa opera cada competição | |
| Vários administradores no mesmo escopo | Compartilham o UUID da competição; auditoria e concorrência distinguem as ações | ✓ |

**Escolha do responsável:** vários `ADMINISTRADOR_COMPETICAO` por competição.
**Notas:** administrador geral atribui o escopo; administrador de competição não concede papéis.

---

## Chaveamentos e classificação

| Opção | Descrição | Selecionada |
|---|---|---|
| Sorteio pela BETUFU | A plataforma cria confrontos | |
| Transcrição do chaveamento oficial | Administrador registra a fonte; chave deriva das partidas | ✓ |
| Sem chaveamento | Apenas lista de jogos | |

**Escolha do responsável:** transcrição manual do chaveamento oficial.
**Notas:** grupos e pontos corridos mostram jogos/resultados, mas a classificação é transcrita do boletim no MVP; desempates automáticos ficam para o futuro.

---

## Cadastro e vínculo de atletas

| Opção | Descrição | Selecionada |
|---|---|---|
| Comprovante documental | Arquivo privado e revisão humana | |
| Matrícula transitória e HMAC | Administrador digita; backend calcula HMAC e descarta a matrícula | ✓ |
| Somente nome | Não permite propor vínculo posterior com conta | |

**Escolha do responsável:** matrícula transitória com o mesmo HMAC da conta, sem comprovante.
**Notas:** correspondência gera proposta que a pessoa aceita ou rejeita; não existe vínculo automático silencioso nem selo oficial da UFU.

---

## Importação de elenco

| Opção | Descrição | Selecionada |
|---|---|---|
| Somente cadastro individual | Mais simples, mas trabalhoso para elencos grandes | |
| Individual + CSV oficial | Modelo fixo UTF-8, validação completa e importação atômica | ✓ |
| XLSX flexível | Várias planilhas, fórmulas e colunas customizadas já no MVP | |

**Escolha do responsável:** cadastro individual e CSV oficial.
**Notas:** separar parser da regra de importação para permitir XLSX futuro sem reescrever validação, HMAC, transação ou auditoria.

---

## Perfil público do atleta

| Opção | Descrição | Selecionada |
|---|---|---|
| Perfil esportivo mínimo | Nome, foto e equipe | |
| Perfil esportivo ampliado | Foto, curso, biografia e redes opcionais, além de dados esportivos oficiais | ✓ |
| Perfil com dados pessoais administrativos | Inclui matrícula/e-mail/identificadores internos | |

**Escolha do responsável:** perfil ampliado controlado pelo próprio atleta após aceitar o vínculo.
**Notas:** estatísticas específicas por esporte ficaram para depois; o MVP mostra somente métricas genéricas confiáveis.

---

## Política de apostas para atletas

| Opção | Descrição | Selecionada |
|---|---|---|
| Bloquear eventos da própria equipe | Reduz conflito, mas exclui participantes importantes e amplia a lógica do MVP | |
| Permitir somente a favor | Ainda usa informação interna e cria regra difícil de explicar | |
| Não restringir atleta no MVP | Permite qualquer aposta e registra reavaliação futura | ✓ |

**Escolha do responsável:** atleta pode apostar inclusive na própria equipe/evento, a favor ou contra.
**Notas:** risco aceito para créditos virtuais; as relações esportivas devem continuar modeladas para política futura. Restrições administrativas permanecem.

---

## Publicação de resultados

| Opção | Descrição | Selecionada |
|---|---|---|
| Mesmo administrador pode confirmar | Ajuda de outra pessoa é opcional; auditoria e fonte continuam obrigatórias | ✓ |
| Segundo administrador obrigatório | Maior segregação de função, porém mais operação para o MVP | |
| Resultado só na Fase 6 | Deixa agenda e chaveamento incompletos | |

**Escolha do responsável:** o mesmo administrador pode registrar e confirmar, inclusive para a liquidação futura do MVP.
**Notas:** `registradoPor` e `confirmadoPor` permanecem separados; duas pessoas tornam-se evolução obrigatória antes de prêmio real, escala maior ou parceria institucional. O boletim oficial corretivo mais recente prevalece e o histórico anterior não é apagado.

---

## Locais

| Opção | Descrição | Selecionada |
|---|---|---|
| Local reutilizável + espaço opcional | Ex.: Centro Esportivo UFU → Ginásio 05 | ✓ |
| Texto livre por evento | Duplica endereço e dificulta filtro | |
| Somente local principal | Não distingue quadra, campo ou ginásio | |

**Escolha do responsável:** local reutilizável com espaço interno opcional.
**Notas:** endereço, mapa e observações também são opcionais; `LOCAL_A_DEFINIR` é estado válido.

---

## Discrição dos agentes posteriores

- detalhes técnicos de pacotes, contratos internos, paginação, códigos de erro, cache e concorrência;
- limites concretos de foto, biografia e CSV;
- microtextos e composição visual, sujeitos a um `UI-SPEC.md` antes da implementação frontend.

## Ideias adiadas

- restrição de apostas para atletas;
- confirmação financeira obrigatória por duas pessoas;
- estatísticas específicas por modalidade;
- escalações por partida;
- importação XLSX;
- classificação/desempate automáticos;
- operação completa de provas multi-competidor;
- integração automática com fontes oficiais;
- comprovantes documentais, se uma necessidade futura justificar a coleta.
