# Síntese da Pesquisa do Projeto

**Projeto:** BETUFU  
**Domínio:** aplicação web social de palpites esportivos universitários com créditos exclusivamente virtuais  
**Pesquisado em:** 2026-08-01  
**Confiança geral:** MÉDIA-ALTA

## Resumo Executivo

A BETUFU deve ser lançada como uma experiência social e informativa para uma edição das Olimpíadas UFU, não como uma casa de apostas nem como produto oficial da universidade. O conteúdo esportivo público — agenda, partidas, equipes, elencos e resultados — cria descoberta e contexto; contas verificadas ganham acesso aos palpites dos jogos internos e ao ranking UFU. O ciclo mínimo confiável é cadastrar e publicar um confronto, publicar odds revisadas, aceitar um palpite simples antes do fechamento, liquidá-lo de modo reproduzível e refletir o resultado em saldo, histórico e rankings. Páginas de equipes, pools privados, conquistas e chat moderado continuam no MVP por decisão do projeto; o corte deve ocorrer na quantidade de edições, modalidades e mercados.

A implementação recomendada é um **monólito modular full-stack em TypeScript**, com Next.js no runtime Node.js e PostgreSQL como fonte única de verdade. Supabase reúne PostgreSQL, Auth, Storage privado e Realtime; Vercel hospeda a aplicação na mesma região de São Paulo. Apostas, ledger, odds, resultados, liquidação, elegibilidade e auditoria passam sempre por comandos server-side e transações do PostgreSQL. Realtime apenas anuncia dados já persistidos; projeções de ranking e conquistas podem ser eventualmente consistentes e reconstruíveis. A operação inicial é deliberadamente assistida por administradores e deve oferecer rascunho, revisão, publicação, correção e filas de falha rastreáveis.

Os maiores riscos são mudança inadvertida para uma economia com valor real, aparência de vínculo oficial com a UFU, vazamento de comprovantes, inconsistência concorrente de saldo/liquidação e perda de confiança por odds, resultados ou moderação não reproduzíveis. A mitigação exige gates antes do piloto público: revisão jurídica e de marca, política de privacidade e retenção, ledger append-only com idempotência, odds/resultados versionados, máquinas de estado separadas, conflitos de interesse aplicados no servidor, auditoria no mesmo commit e testes de concorrência. A grade piloto só deve habilitar palpites onde houver fonte, horário e regra de resultado confiáveis.

## Principais Achados

Os detalhes completos permanecem em [STACK.md](./STACK.md), [FEATURES.md](./FEATURES.md), [ARCHITECTURE.md](./ARCHITECTURE.md) e [PITFALLS.md](./PITFALLS.md).

### Stack Recomendada

O conjunto tecnológico reduz a operação sem terceirizar as invariantes do produto. Framework, autenticação, realtime e storage são aceleradores; a autoridade permanece nos módulos de domínio e no PostgreSQL. Versões de framework, Auth, ORM, driver e cálculo devem ser fixadas exatamente no `package.json` e no lockfile.

**Tecnologias centrais:**

- **Node.js 24.18.1 LTS + pnpm 11.18.0:** runtime e instalações determinísticas; não adotar Node 26 Current em produção.
- **Next.js 16.2.12 App Router + React 19.2.8:** uma aplicação responsiva e um deploy; Server Components por padrão e hidratação apenas de bilhete, chat e superfícies administrativas interativas.
- **TypeScript 6.0.3 em modo `strict`:** compatível com o tooling atual; TypeScript 7 deve esperar suporte estável da Compiler API no ecossistema.
- **PostgreSQL 17 no Supabase (`sa-east-1`):** fonte de verdade para domínio esportivo, odds, apostas, ledger, resultados, rankings, chat e auditoria; não depender de recursos exclusivos do PostgreSQL 18.
- **Drizzle ORM 0.45.2 + Postgres.js 3.4.9:** SQL visível, migrations revisáveis, constraints e transações; usar `prepare: false` no Supavisor Transaction mode e conexão direta para migrations.
- **Supabase Auth + SSR, Storage e Realtime:** sessão PKCE/cookies, comprovantes em bucket privado e Broadcast from Database em canais privados; RBAC de negócio continua no banco e no servidor.
- **Vercel `gru1`:** runtime Node próximo ao banco; nunca usar Edge runtime para aposta, ledger, odds ou liquidação.
- **Zod 4.4.3 + `BIGINT`/`NUMERIC` + decimal.js 10.6.0:** validação na borda e aritmética exata; créditos não usam ponto flutuante.
- **Tailwind 4.3.3 + Radix/shadcn:** UI mobile-first, acessível e com identidade própria, sem estética ou símbolos de dinheiro real.
- **Vitest + PostgreSQL real + Playwright:** regras puras, integração, concorrência e jornadas mobile/desktop; mocks de ORM não validam o núcleo transacional.
- **Pino + Sentry:** logs e erros com redaction e sem PII por padrão; a auditoria de negócio continua separada e append-only.

**Decisão sobre Redis:** não é dependência do núcleo nem fonte de verdade. Começar sem Redis no piloto fechado é aceitável; habilitar Upstash apenas no hardening quando rate limiting distribuído for necessário para chat, denúncias, uploads ou comandos sensíveis em múltiplas instâncias. Saldo, sessão de negócio, odds e resultados nunca ficam nele.

### Funcionalidades Esperadas

#### Obrigatórias — table stakes do MVP

- **Identidade e descoberta pública:** cadastro, autenticação, recuperação, apelido, equipe favorita, agenda filtrável e estados claros.
- **Conteúdo esportivo útil:** página de partida e páginas de equipes com modalidade, elenco, próximos jogos, resultados e forma recente.
- **Operação assistida:** console com RBAC para competição, edição, modalidades, participantes, partidas, mercados, odds e resultados, sempre com rascunho/publicação e auditoria.
- **Verificação UFU privada:** upload temporário, revisão manual, uma conta por matrícula, expiração periódica e exclusão física verificável do comprovante.
- **Economia fechada:** concessão controlada, ledger explicável e ausência estrutural de compra, venda, saque, prêmio, conversão ou transferência.
- **Palpite simples pré-jogo:** stake, quote versionada, retorno potencial, confirmação, fechamento pelo relógio do banco, odd aceita imutável e palpites alheios ocultos até o prazo.
- **Resultado e liquidação completos:** vitória, derrota, empate quando aplicável, adiamento, cancelamento, `void`, estorno e correção idempotente.
- **Competição social:** histórico pessoal, rankings geral/UFU/amigos, pools privados com convite e administração básica, desempate público e conquistas aprovadas.
- **Chat seguro por partida:** mensagens persistidas, denúncia, silenciamento, remoção, banimento, rate limit e ações administrativas auditadas.
- **Integridade e entrega:** bloqueios de atleta/equipe e admin/evento, trilha de auditoria, responsividade, acessibilidade básica e linguagem clara de independência e recreação.

#### Diferenciadores a preservar

- **Relevância local:** páginas de equipes e partidas funcionam como polos de informação e torcida das Olimpíadas UFU.
- **Acesso público com competição verificada:** a verificação restringe ações elegíveis, não a descoberta do produto.
- **Privacidade demonstrável:** exclusão pós-revisão, status visível ao usuário e armazenamento apenas do mínimo necessário.
- **Odds transparentes:** sugestão explicável, nível de confiança, fallback neutro/manual, aprovação humana e histórico público de versões.
- **Integridade contextual:** elegibilidade por vínculo vigente, conflitos de interesse e escopo administrativo, não apenas um papel global.
- **Status sem recompensa financeira:** conquistas, pools, torcida e rankings dão motivo de retorno sem criar valor econômico.
- **Social contextual:** chat ligado à partida e à equipe, com moderação desde o primeiro dia.

#### Adiar para v1.x

- Notificações opt-in e menções, depois de estabilizar eventos e preferências.
- Contestação formal de resultado/liquidação, com prazo, evidência e snapshots.
- Estatísticas avançadas, sabedoria da torcida pós-fechamento e métricas por modalidade/período.
- Reações/respostas no chat, torcedômetro e duelos entre torcidas.
- Importação/revisão em lote e administração avançada de pools, somente se a operação manual provar o gargalo.

#### Adiar para v2+

- Mercados multicompetidor para atletismo, natação e formatos afins.
- Arquivo permanente de edições e rivalidades.
- Novas competições, campi ou universidades e integrações oficiais.
- Quizzes/votações e aplicativos nativos.

**Fora do horizonte atual:** dinheiro real, prêmio de valor econômico, compra/saque/conversão/transferência de créditos, apostas ao vivo e apostas múltiplas. Não devem existir como flags ocultas.

### Abordagem de Arquitetura

O sistema deve ter uma implantação web e módulos internos com propriedade explícita de dados, contratos públicos estreitos e uma unidade de trabalho transacional compartilhada. Escritas críticas são fortemente consistentes; ranking, conquistas, broadcast, exclusão física de documentos e outras consequências pós-commit usam outbox/jobs idempotentes. O executor começa como função curta do mesmo código-base acionada por Cron; fila ou worker separado só entram quando backlog e carga medidos exigirem.

**Componentes principais:**

1. **Identidade e Acesso** — perfis, papéis, suspensões, escopos e políticas RBAC + ABAC/ReBAC.
2. **Verificação Estudantil** — solicitações, HMAC da matrícula, validade, decisão e ciclo de vida do documento efêmero.
3. **Catálogo Esportivo** — competição, edição, modalidade, categoria, participante, equipe, atleta, fase, evento e afiliações temporais.
4. **Mercados e Odds** — templates, recomendação, revisão e conjuntos publicados imutáveis.
5. **Apostas** — bilhete simples, stake, quote aceita, retorno potencial e estado do palpite.
6. **Ledger** — contas, transações e postings balanceados, reversões, saldo projetado e reconciliação.
7. **Resultados e Liquidação** — versões de resultado, runs retomáveis, outcomes, `void` e correções compensatórias.
8. **Social e Moderação** — salas por partida, mensagens, denúncias, mutes, bans e evidência mínima.
9. **Rankings e Conquistas** — read models reconstruíveis, pools privados, membros e badges.
10. **Auditoria e Operações** — eventos append-only, outbox, jobs, Storage, Realtime e observabilidade.
11. **App Router/Admin UI** — composição de telas e adaptação HTTP; nenhuma regra crítica reside na UI.

**Padrões obrigatórios:**

- PostgreSQL como única autoridade; cache e realtime são descartáveis.
- Contratos internos, sem HTTP entre módulos e sem escrita direta na tabela de outro módulo.
- Ledger append-only em partidas dobradas ou equivalente balanceado; correções por reversão.
- Odds e resultados publicados imutáveis e versionados; aposta referencia a quote exata.
- Máquinas de estado separadas para evento, janela de palpites e liquidação.
- Autorização deny-by-default em todo comando; RLS é defesa em profundidade, não regra exclusiva.
- Idempotência baseada em chave + fingerprint de payload + constraints `UNIQUE`.
- Auditoria sanitizada no mesmo commit da ação crítica.
- Outbox somente para efeitos que podem atrasar; aposta + débito e liquidação + ledger continuam atômicos.
- Sport rule adapters versionados; confrontos diretos primeiro, sem condicionais espalhadas por modalidade.
- Chat persistido e autorizado antes do Broadcast; reconexão sempre recupera histórico por cursor.

### Cinco Maiores Riscos

1. **Economia virtual adquirir valor real** — proibir por regras de produto, banco, UI e testes qualquer compra, transferência, resgate, prêmio ou benefício econômico; realizar revisão jurídica e de política etária antes do piloto público e tratar monetização futura como outro projeto.
2. **BETUFU aparentar vínculo oficial com a UFU** — identidade visual própria, aviso de independência em pontos relevantes e revisão específica do nome, domínio, textos, logos e direitos de imagem; conteúdo público não equivale a licença de uso.
3. **Comprovantes ou matrículas vazarem ou permanecerem armazenados** — bucket privado, signed URLs curtas, MFA para revisores, allowlist/magic bytes/limites, HMAC com pepper fora do banco, `deletion_pending`, retry e reconciliação; nunca guardar matrícula crua, signed URL ou bytes em logs/backups próprios.
4. **Saldo, odds ou liquidação perderem a rastreabilidade** — transações curtas, locks/updates condicionais, ledger balanceado, aritmética exata, idempotência e versões imutáveis; repetir aposta, liquidação, `void` ou correção deve produzir o mesmo resultado sem débito/pagamento duplicado.
5. **Operação esportiva/social comprometer a confiança** — separar estados de evento/palpite/liquidação, exigir fonte e motivo em publicações/correções, bloquear conflitos no servidor, manter picks ocultos até o fechamento e lançar chat apenas com denúncia, mute, ban, retenção e capacidade real de moderação.

## Implicações para o Roadmap

A pesquisa recomenda dez fases. Cada fase deve terminar com uma capacidade verificável, não apenas com schema ou UI parcial.

### Fase 1: Fundação, Identidade e Gates de Segurança

**Racional:** todas as capacidades dependem do runtime, banco, sessão, políticas, UI responsiva e limites econômicos/institucionais definidos.  
**Entrega:** scaffold versionado, ambientes Supabase/Vercel em São Paulo, migrations, unit of work, Auth, perfil/equipe favorita, RBAC básico, auditoria/idempotência/observabilidade, design tokens e avisos de independência/recreação.  
**Abrange:** cadastro, recuperação, perfil público, responsividade e acessibilidade básica.  
**Evita:** autorização apenas na UI, deriva para dinheiro real, estética de sportsbook e rebranding tardio.

### Fase 2: Catálogo Esportivo e Operação Administrativa

**Racional:** partidas, odds, apostas, chat e páginas públicas dependem de um modelo esportivo e de dados operáveis.  
**Entrega:** competição/edição/modalidade/categoria/equipe/atleta/fase/evento, afiliações com vigência, estados separados, console rascunho/publicação e agenda/páginas de equipe/partida.  
**Abrange:** descoberta pública, conteúdo local, fontes/proveniência e operação assistida.  
**Evita:** modelo `home/away` rígido, status único e parser externo escrevendo diretamente em produção.

### Fase 3: Verificação UFU e Integridade Contextual

**Racional:** elegibilidade e conflitos precisam existir antes de qualquer palpite real.  
**Entrega:** upload temporário, revisão manual com MFA, status/expiração/reenvio, unicidade por HMAC, deleção verificável, afiliação atleta–equipe e atribuição admin–evento.  
**Abrange:** verificação opcional, uma conta por matrícula, ranking UFU elegível e bloqueios de conflito.  
**Evita:** documento permanente, hash enumerável, acesso excessivo e confusão entre vínculo estudantil e integridade esportiva.

### Fase 4: Economia Fechada e Ledger

**Racional:** saldo correto e reversível é pré-requisito para aceitar uma stake.  
**Entrega:** política versionada de créditos, contas/postings balanceados, concessão inicial, saldo projetado, reversão, idempotência, reconciliação e suíte concorrente.  
**Abrange:** saldo, histórico de transações e ausência de fluxos de compra/transferência/conversão.  
**Evita:** saldo mutável irreconciliável, ponto flutuante, duplo gasto e recargas discricionárias.

### Fase 5: Mercados e Odds Versionadas

**Racional:** o bilhete só pode aceitar uma quote publicada, reproduzível e adequada à modalidade.  
**Entrega:** adapters de confronto direto, tipos simples de mercado, recomendação explicável, fallback neutro/manual, revisão administrativa, publicação imutável, histórico e regra única de arredondamento.  
**Abrange:** odds sugeridas, transparência, estado de confiança e revisão humana.  
**Evita:** falsa precisão, alteração retroativa, condições por esporte espalhadas e cotação sem evidência.

### Fase 6: Palpite Simples Pré-jogo

**Racional:** integra elegibilidade, mercado e ledger já testados numa única transação.  
**Entrega:** bilhete, quote ID, stake/retorno, reconfirmação de odd alterada, fechamento pelo relógio do banco, débito atômico, pick oculto e histórico pessoal.  
**Abrange:** ciclo principal de palpite e prova da condição aceita.  
**Evita:** aposta fora do prazo, gasto duplo, confiança no cliente e vazamento de picks em API/cache/realtime.

### Fase 7: Resultados, Liquidação e Correções

**Racional:** rankings e conquistas só podem consumir fatos liquidados, não resultados crus.  
**Entrega:** resultado versionado, `settlement_run`, win/loss/empate/`void`, adiamento/cancelamento, replay seguro, reversão e reliquidação com motivo.  
**Abrange:** apuração determinística, saldo pós-evento e correções auditáveis.  
**Evita:** job presumido exatamente uma vez, edição manual de saldo e mistura entre estados da partida e liquidação.

### Fase 8: Rankings, Pools e Conquistas

**Racional:** a camada competitiva depende de ledger e liquidações reconciliáveis.  
**Entrega:** regras versionadas, projeções reconstruíveis, critérios de desempate, rankings geral/UFU/privado, convite/administração básica de pool e conquistas de sequência, azarão, rodada e torcida.  
**Abrange:** recompensa social sem dinheiro e aquisição por amigos.  
**Evita:** ranking opaco, vantagem irreproduzível de entrada/recarga, mudança de fórmula durante a edição e lógica duplicada de vitória.

### Fase 9: Chat e Moderação Realtime

**Racional:** chat depende de identidade, partidas, sanções e capacidade operacional; não deve anteceder a política de abuso.  
**Entrega:** mensagens persistidas, paginação por cursor, Broadcast privado pós-commit, denúncia, mute, ban, remoção, fila/admin, retenção, revogação de sessão e rate limiting proporcional à carga.  
**Abrange:** conversa contextual segura por partida.  
**Evita:** broadcast cliente-a-cliente, HTML arbitrário, evidência perdida, canal excessivamente autorizado e histórico ilimitado.

### Fase 10: Hardening e Piloto Operacional

**Racional:** o lançamento reúne riscos concorrentes, documentais, operacionais e reputacionais que testes unitários isolados não cobrem.  
**Entrega:** ensaio completo da edição, seeds e fontes verificadas, jobs/retries observáveis, reconciliação Ledger/Storage, testes E2E/concorrência/carga/segurança, backup e restore, runbooks, SLOs, QA mobile e gates jurídico, de privacidade e de marca concluídos.  
**Abrange:** prontidão de operação, monitoramento e lançamento controlado.  
**Evita:** modalidade sem dado confiável, falhas silenciosas, comprovantes órfãos, plano Supabase subdimensionado e lançamento sem capacidade de moderação.

### Racional da Ordem

- Identidade, autorização, auditoria e limites de produto precedem qualquer dado real ou ação sensível.
- Catálogo e relações temporais precedem elegibilidade, odds, conflitos, chat e páginas públicas.
- Ledger e odds são construídos e testados separadamente antes de serem unidos pelo comando de palpite.
- Liquidação precede todos os derivados competitivos; ranking e conquistas nunca criam sua própria definição de resultado.
- Chat é requisito do MVP, mas entra depois das fundações de identidade, partida, sanção e administração.
- Hardening não corrige arquitetura tarde; ele comprova invariantes, capacidade operacional e gates externos antes do piloto.

### Flags de Pesquisa

**Fases que exigem pesquisa ou validação adicional no planejamento:**

- **Fase 1 — marca, enquadramento e política etária:** requer validação jurídica brasileira e revisão do nome/identidade; pesquisa técnica não substitui parecer profissional.
- **Fase 2 — grade e fontes do piloto:** selecionar modalidades, formatos, locais e fontes reais; definir proveniência, licença e fallback administrativo antes dos adapters.
- **Fase 3 — tratamento documental:** decidir hipótese legal, aviso, retenção/SLO, contrato do operador, visualização segura e eventual antimalware.
- **Fase 5 — odds com poucos dados:** pesquisar e simular Elo/Bradley–Terry ou alternativa transparente, calibração, margem recreativa e fallback por modalidade.
- **Fase 8 — economia e ranking:** simular saldo inicial, reposição, entrada tardia, métricas, desempates, `void` e relação entre populações geral/UFU.
- **Fase 9 — moderação e escala realtime:** definir política comunitária/retenção em português, capacidade de atendimento, limites do plano e gatilho objetivo para Upstash.
- **Fase 10 — capacidade do piloto:** teste de carga com o plano contratado, SLOs, restore e ensaio operacional dependem da grade e audiência estimadas.

**Fases com padrões bem documentados — podem dispensar `/gsd-research-phase`:**

- **Fase 4 — Ledger:** padrões de journal, idempotência, constraints e concorrência são conhecidos; investir em plano e testes contra PostgreSQL real.
- **Fase 6 — Palpite simples:** transação, locks, clock do banco e quote imutável estão prescritos; foco em critérios de aceitação e testes de corrida.
- **Fase 7 — Liquidação:** versionamento, reversão e replay seguro estão definidos; a única pesquisa adicional vem de regra esportiva nova.
- **Partes técnicas da Fase 1:** scaffold Next/Supabase, migrations, Auth SSR e observabilidade seguem documentação oficial; os gates jurídico/brand continuam externos.

## Avaliação de Confiança

| Área | Confiança | Observações |
|---|---|---|
| Stack | ALTA | Versões, peers, regiões e capacidades foram verificadas em documentação oficial e registries; custo e dimensionamento do plano dependem do piloto. |
| Funcionalidades | MÉDIA-ALTA | Table stakes aparecem repetidamente em produtos de referência e as decisões do MVP estão claras; prioridade fina, retenção e aceitação da verificação ainda não têm dados de usuários BETUFU. |
| Arquitetura | ALTA | Transações, locks, RLS, Storage, Realtime, outbox e modularidade são padrões bem documentados; limiares de extração e materialização dependem de carga real. |
| Riscos | ALTA no técnico; MÉDIA no jurídico/marca | Segurança, privacidade e consistência têm controles claros; não há conclusão definitiva sobre enquadramento de pontos virtuais nem autorização do nome/marca. |

**Confiança geral:** MÉDIA-ALTA. A direção técnica e a sequência de dependências são firmes; a prontidão de lançamento depende de decisões externas, dados reais das Olimpíadas e validação do comportamento dos usuários.

### Lacunas a Resolver

- **Enquadramento jurídico e idade:** validar termos, comunicação, política etária e o modelo gratuito sem assumir isenção automática por usar créditos virtuais.
- **Nome, marca e direitos de conteúdo:** revisar BETUFU, sigla UFU, domínio, identidade, logos de equipes/atléticas e imagens de atletas antes de publicar.
- **Grade e qualidade de dados:** confirmar edição, modalidades, formatos, horários, locais, rosters, fontes e responsáveis por correção.
- **Privacidade da verificação:** documentar controlador/operadores, hipótese legal, retenção, canal do titular, SLO de exclusão, backups e resposta a incidente.
- **Visualização de upload:** escolher abordagem segura para PDF/imagem e decidir necessidade/fornecedor de varredura antimalware.
- **Política econômica:** congelar unidade atômica, saldo inicial, reposição, arredondamento, entrada tardia, temporada, empate e `void` antes da primeira migration do Ledger.
- **Métrica de ranking:** simular saldo, lucro virtual, retorno percentual, acurácia e estratégias de alto risco; publicar versão e desempates.
- **Modelo de odds:** validar disponibilidade histórica, features, calibração e limiar de confiança para fallback neutro/manual.
- **Operação e moderação:** estimar volume, SLA de revisão/verificação/resultado/denúncia, papéis disponíveis e plano de cobertura durante jogos.
- **Capacidade de infraestrutura:** medir usuários simultâneos, mensagens/s, conexões, latência transacional, custo e limites do plano Supabase/Vercel.
- **Hipóteses de produto:** medir consulta de conteúdo sem palpite, conversão da verificação, convites de pools, compreensão das odds, saúde do chat e retorno por conquistas.

## Fontes

### Pesquisa consolidada

- [Stack detalhada](./STACK.md) — versões, compatibilidade, serviços e política de atualização.
- [Funcionalidades detalhadas](./FEATURES.md) — benchmarks, table stakes, diferenciais, anti-features e hipóteses.
- [Arquitetura detalhada](./ARCHITECTURE.md) — módulos, invariantes, fluxos, concorrência, realtime e escala.
- [Riscos detalhados](./PITFALLS.md) — armadilhas, sinais, prevenção, recuperação e gates.

### Primárias e normativas — alta confiança

- [Node.js 24.18.1 LTS](https://nodejs.org/en/blog/release/v24.18.1) e [Next.js 16](https://nextjs.org/docs/app/guides/upgrading/version-16) — runtime e framework.
- [Anúncio oficial do TypeScript 7.0](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/) — razão para manter TypeScript 6.x no tooling do MVP.
- [PostgreSQL: isolamento transacional](https://www.postgresql.org/docs/current/transaction-iso.html), [locks](https://www.postgresql.org/docs/current/explicit-locking.html) e [constraints](https://www.postgresql.org/docs/current/ddl-constraints.html) — consistência e concorrência.
- [Supabase Auth/RBAC](https://supabase.com/docs/guides/api/custom-claims-and-role-based-access-control-rbac), [Realtime Broadcast](https://supabase.com/docs/guides/realtime/broadcast) e [exclusão de objetos](https://supabase.com/docs/guides/storage/management/delete-objects) — identidade, canais privados e ciclo de vida do comprovante.
- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html), [File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html) e [WebSocket Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html) — autorização, uploads e chat.
- [LGPD — Lei nº 13.709/2018](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm) e [regulamento de incidentes da ANPD](https://www.gov.br/anpd/pt-br/canais_atendimento/agente-de-tratamento/comunicado-de-incidente-de-seguranca-cis) — necessidade, eliminação, segurança e incidentes.
- [Lei nº 14.790/2023](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2023/lei/l14790.htm) e [legislação atualizada do Ministério da Fazenda](https://www.gov.br/fazenda/pt-br/composicao/orgaos/secretaria-de-premios-e-apostas/apostas-de-quota-fixa/legislacao) — referência de risco para preservar a ausência de valor econômico; não constitui parecer sobre a BETUFU.
- [Regulamentação da Marca UFU](https://comunica.ufu.br/marca-ufu) — necessidade de revisão/autorização específica de uso.

### Benchmarks de produto — confiança média a alta

- [BeTeam](https://beteamapp.com/), [Kicktipp](https://www.kicktipp.com/info/professional/functions/), [Superbru](https://www.superbru.com/about) e [RunYourPool](https://help.runyourpool.com/en/articles/6494229-runyourpool-guide-dashboard-connections) — núcleo de pools, prazo, histórico, ranking, convites e comunidade.
- [Inter USP 2026](https://apps.apple.com/br/app/inter-usp-2026/id6769940648), [APP Esportivo](https://appesportivo.com.br/) e [IMLeagues na UW–Whitewater](https://www.uww.edu/rec-sports/programs/intramural-sports/im-leagues) — agenda, modalidade, equipes, elencos, locais, resultados e elegibilidade em esporte universitário/amador.

---
*Pesquisa concluída: 2026-08-01*  
*Pronto para o roadmap: sim, com os gates e pesquisas de fase acima*
