# Pesquisa de Funcionalidades

**Domínio:** jogo social universitário de previsões esportivas, sem dinheiro real  
**Projeto:** BETUFU  
**Pesquisado:** 2026-08-01  
**Confiança:** ALTA para recursos observados nos produtos e para os requisitos regulatórios citados; MÉDIA para a priorização, que ainda precisa ser validada com usuários e com a capacidade operacional do piloto

## Síntese do Panorama

O padrão consolidado dos jogos sociais de previsão é simples: entrar em um grupo, registrar um palpite antes do prazo, ter esse registro preservado, acompanhar a apuração e comparar o resultado em um ranking. BeTeam, Kicktipp, Superbru e RunYourPool repetem esse núcleo com variações de pontuação, convites, estatísticas e recursos comunitários. Portanto, cadastro, fechamento pré-jogo, apuração consistente, histórico, ranking e pools privados não diferenciam o BETUFU; a ausência de qualquer um deles faz o produto parecer incompleto.

O diferencial defensável está na interseção de três domínios que os concorrentes normalmente tratam separados:

1. **Previsão social sem valor financeiro**, com créditos estritamente virtuais;
2. **Cobertura universitária local**, com modalidades, equipes, elencos, locais e resultados das Olimpíadas UFU;
3. **Confiança verificável**, com elegibilidade estudantil, impedimentos por conflito de interesse, odds revisadas e histórico de alterações.

Plataformas universitárias e de torneios, como Inter USP 2026, APP Esportivo e IMLeagues, estabelecem outra expectativa: a aplicação deve também funcionar como fonte útil para encontrar programação, modalidade, local, elenco, placar e classificação. O palpite não substitui esse conteúdo; ele depende dele. A página da partida deve ser o ponto de encontro entre informação, palpite e conversa.

**Recomendação central:** lançar o piloto como uma experiência fechada e confiável de uma edição das Olimpíadas UFU, com poucos mercados simples e boa cobertura operacional. Não tentar vencer concorrentes pela quantidade de esportes, mercados ou formatos. Vencer pela relevância local, pela transparência e pela competição entre pessoas e equipes que já compartilham uma comunidade.

## Paisagem de Funcionalidades

### Table Stakes — usuários esperam

| Funcionalidade | Por que é esperada | Complexidade | Dependências principais | Horizonte |
|---|---|---:|---|---|
| Cadastro, autenticação, recuperação de acesso e perfil público | Todo pool social precisa identificar participantes e dar contexto ao ranking; apelido e equipe favorita transformam uma linha de tabela em uma pessoa reconhecível | MÉDIA | Identidade, política de nomes, privacidade | MVP |
| Agenda navegável por data, modalidade, categoria e fase | Em produtos esportivos universitários, a primeira pergunta é “quando, onde e quem joga?” | MÉDIA | Modelo de competição, fonte/cadastro confiável, estados de partida | MVP |
| Página da partida com horário, local, equipes, mercado, odds e estado | Centraliza a decisão do palpite e reduz ambiguidades sobre qual evento está sendo previsto | MÉDIA | Agenda, equipes, publicação administrativa de mercados | MVP |
| Página de equipe com elenco, modalidade, próximos jogos, resultados e forma recente | Inter USP, APP Esportivo e IMLeagues mostram que equipes, programação e resultados são conteúdo básico do domínio universitário/amador | MÉDIA | Entidades de equipe/atleta/modalidade, resultados | MVP |
| Regras visíveis antes de participar | Usuários precisam entender saldo inicial, fechamento, cálculo do retorno, desempate, cancelamento e efeito no ranking | MÉDIA | Regras versionadas e políticas operacionais | MVP |
| Bilhete simples pré-jogo | Selecionar um único mercado, informar créditos, ver odd aceita e retorno potencial é o ciclo principal do produto | ALTA | Conta elegível, saldo, mercado publicado, odd ativa, relógio/estado da partida | MVP |
| Saldo e razão contábil de créditos virtuais | O usuário espera saldo correto e explicação de cada débito, estorno e crédito; recalcular apenas a partir do “saldo atual” é frágil | ALTA | Livro-razão imutável, idempotência, regras de distribuição | MVP |
| Fechamento inequívoco antes do início | BeTeam e Kicktipp tratam prazo e bloqueio como requisito de justiça; o servidor, e não o relógio do navegador, deve decidir | ALTA | Horário canônico, estado da partida, transação atômica | MVP |
| Palpites alheios ocultos até o fechamento | Evita cópia, influência indevida e vantagem para quem aposta por último; Kicktipp explicita essa configuração | MÉDIA | Controle de visibilidade, fechamento | MVP |
| Registro imutável do palpite e da odd aceita | Permite provar “o que foi registrado” e calcular exatamente a condição aceita, mesmo se a odd mudar depois | ALTA | Versionamento de odds, auditoria, bilhete | MVP |
| Resultado, liquidação e estorno determinísticos | O ciclo não termina no palpite: precisa tratar vitória, derrota, empate, cancelamento, adiamento e correção sem duplicar créditos | ALTA | Resultado oficial/admin, estados terminais, livro-razão, idempotência | MVP |
| Histórico pessoal de palpites e transações | Produtos de referência preservam escolhas e resultados; sem histórico, o usuário não consegue conferir desempenho nem confiar no saldo | MÉDIA | Bilhete, liquidação, livro-razão | MVP |
| Rankings com regra e desempate explícitos | Ranking geral, ranking UFU e ranking de amigos são a recompensa principal quando não há dinheiro | ALTA | Métrica versionada, liquidações, elegibilidade, privacidade | MVP |
| Pool privado com convite simples | Convite por link/código e lista de participantes são padrão em BeTeam, Kicktipp, Superbru e RunYourPool | MÉDIA | Identidade, autorização de grupo, ranking filtrado | MVP |
| Administração básica do pool privado | Criador precisa nomear, convidar/remover membros e encerrar o grupo; regras financeiras ou mercados customizados não devem ser liberados | MÉDIA | Papéis, autorização, auditoria | MVP |
| Chat contextual por partida | A conversa é parte do valor social e deve estar junto do evento, não perdida em planilha ou grupo externo | ALTA | Partida, identidade, tempo real ou atualização frequente | MVP |
| Denúncia, silenciamento e moderação do chat | Chat sem controles de segurança gera custo reputacional e operacional; reportar mensagem, silenciar usuário e remover conteúdo são o mínimo | ALTA | Papéis, fila de moderação, evidência/auditoria, política comunitária | MVP |
| Console operacional para publicar e corrigir dados | No piloto, partidas, odds, verificações e resultados dependem de administradores; a operação assistida precisa ser rápida e rastreável | ALTA | RBAC, workflow de rascunho/publicado, auditoria | MVP |
| Estados e mensagens de exceção claros | “Sem dados”, “aguardando odd”, “fechado”, “adiado”, “cancelado”, “em revisão” e “liquidado” evitam falsas promessas e suporte manual | MÉDIA | Máquina de estados de partida/mercado/aposta | MVP |
| Web responsiva, acessível e tolerante a rede móvel | Acompanhar jogos universitários acontece majoritariamente em celular e em locais com conectividade variável | MÉDIA | Design responsivo, estados de carregamento/erro, acessibilidade | MVP |

### Diferenciadores — vantagem competitiva

| Funcionalidade | Proposta de valor | Complexidade | Dependências principais | Horizonte |
|---|---|---:|---|---|
| Verificação UFU manual, opcional e com expiração | Cria uma competição universitária confiável sem fechar a descoberta pública; uma matrícula só pode validar uma conta | ALTA | Upload temporário seguro, revisão, prevenção de duplicidade, expiração, trilha de decisão | MVP |
| Exclusão do comprovante após revisão | Converte privacidade em confiança perceptível e reduz o impacto de incidente; deve restar somente o mínimo necessário para status e antifraude | ALTA | Política de retenção, job/ação de exclusão verificável, registro sem cópia do documento | MVP |
| Ranking UFU separado do ranking geral | Dá significado real à verificação sem expor matrícula e permite navegação pública a não verificados | MÉDIA | Verificação válida, regras de elegibilidade, ranking | MVP |
| Páginas de equipes como polos de torcida | Faz o produto parecer “dos jogos da UFU”, não um sportsbook genérico; liga elenco, forma, agenda, chat, equipe favorita e conquistas | MÉDIA | Conteúdo esportivo confiável, perfil/equipe favorita | MVP |
| Odds sugeridas com revisão humana e estado de confiança | Usa histórico sem fingir precisão quando a amostra é pequena; administrador pode publicar estimativa neutra ou manual | ALTA | Dados históricos, modelo explicável, workflow de revisão, fallback | MVP |
| Histórico público das odds e metodologia legível | Poucos pools mostram a evolução do multiplicador. Exibir quando, por que categoria de dado e por quem foi alterado reduz suspeita de manipulação | ALTA | Versionamento, auditoria, autorização, explicação sem expor dados indevidos | MVP |
| Créditos explicitamente sem valor financeiro | Mantém o foco em conhecimento, torcida e status; UI, termos e suporte devem reiterar que não há compra, saque, prêmio, conversão ou transferência | MÉDIA | Livro-razão, linguagem do produto, ausência de fluxos financeiros | MVP |
| Impedimentos de integridade por papel e vínculo | Bloquear atleta em jogo da própria equipe e administrador no evento que opera torna o ranking mais crível do que um bolão informal | ALTA | Vínculos atleta–equipe, escopo administrativo, autorização, auditoria | MVP |
| Conquistas ligadas à narrativa universitária | Sequências, azarões, rodadas e apoio à equipe criam status não financeiro e motivos de retorno | MÉDIA | Liquidação, odds aceitas, temporadas, equipe favorita | MVP |
| Conversa ligada à partida e à torcida | O contexto da partida permite debate antes/depois, reações e rivalidade local com moderação mais focalizada que um feed global | ALTA | Chat MVP, estados da partida, moderação | MVP básico; reações na v1.x |
| “Sabedoria da torcida” somente após o fechamento | Percentuais agregados e dispersão dos palpites geram conteúdo social sem induzir cópia antes do prazo | MÉDIA | Volume mínimo, anonimização/agregação, fechamento, regras contra reidentificação | v1.x |
| Desempenho explicável por modalidade e período | Mostra acerto, retorno virtual e posição por rodada/modalidade, separando sorte pontual de consistência | MÉDIA | Histórico suficiente, taxonomia esportiva, métricas estáveis | v1.x |
| Torcedômetro e disputas entre torcidas | Agrega apoio à equipe favorita sem transferir créditos; Inter USP 2026 mostra demanda por representar o time durante o evento | MÉDIA | Equipe favorita, regras anticontas duplicadas, agregação | v1.x |
| Memória de edições e rivalidades | Arquivo de temporadas, medalhas e melhores desempenhos transforma um evento curto em comunidade recorrente, como os clubes persistentes do Superbru | MÉDIA | Edições concluídas, identidades persistentes, regras de arquivo | v2 |
| Expansão multicompetição/campi | Permite novos eventos sem misturar elegibilidade, dados e rankings; só vale após operação do piloto estar comprovada | ALTA | Multi-tenant/escopo por edição, políticas de elegibilidade, governança | v2 |

### Anti-features — não construir

| Funcionalidade | Por que parece atraente | Por que é problemática para o BETUFU | Alternativa recomendada |
|---|---|---|---|
| Dinheiro real, depósitos, saques ou prêmio em dinheiro/bens | Aumentaria intensidade e aparente retenção | Muda materialmente o perfil de risco, operação e regulação. A Lei 14.790/2023 define aposta a partir de valor colocado em risco na expectativa de prêmio; qualquer passo nessa direção exige análise jurídica própria e contradiz o posicionamento aprovado | Créditos sem valor, conquistas, posições e reconhecimento social |
| Compra, venda, conversão ou transferência de créditos | Parece monetizar ou criar economia entre amigos | Cria valor paralelo, fraude, contas-laranja, coerção e possível mercado secundário; também torna o saldo menos comparável | Distribuição controlada pelo sistema e livro-razão não transferível |
| Apostas ao vivo | Produzem muitas ações durante a partida | Exigem feed de baixíssima latência, suspensão automática, prevenção de arbitragem e operação contínua; aumentam comportamento compulsivo e não validam o núcleo pré-jogo | Fechar antes da partida e usar chat/placar para o acompanhamento |
| Apostas múltiplas/acumuladas | Dão sensação de maior desafio e retornos grandes | Multiplicam casos de liquidação, cancelamento e entendimento; deslocam o produto para estética de sportsbook | Um bilhete simples por seleção/mercado; desafios por rodada via conquistas |
| Mercados complexos ou multicompetidor no piloto | Abrangeriam todas as modalidades desde o primeiro dia | Cada prova exige regras próprias de odds, empate, desistência e classificação; dados universitários podem ser incompletos | Começar com confrontos diretos e só ativar modalidade com tabela e resultado confiáveis |
| Revelar palpites ou distribuição da torcida antes do fechamento | Gera conversa e prova social imediata | Favorece cópia, comportamento de manada e vantagem de timing | Ocultar individual e agregado; liberar ambos após o fechamento |
| Odds totalmente automáticas e publicadas sem revisão | Reduz trabalho administrativo | Pouco histórico local cria falsa precisão; erro afeta saldo, ranking e confiança | Sugestão automática com nível de confiança, fallback neutro/manual e aprovação humana |
| Alterar retroativamente a odd de um bilhete | Facilita “corrigir” um erro de publicação | Destrói a auditabilidade da condição aceita e pode gerar tratamento desigual | Congelar odd no bilhete; corrigir apenas eventos futuros e usar estorno explícito quando indispensável |
| Armazenar indefinidamente ou exibir matrícula/comprovante | Facilita nova revisão e prova pública de vínculo | Contraria a minimização/necessidade, aumenta impacto de vazamento e associa identidade civil à atividade social | Excluir comprovante após revisão; guardar somente status, validade e identificador antifraude protegido |
| Chat anônimo, global ou sem moderação | Maximiza volume e reduz fricção de postagem | Aumenta abuso, spam, rivalidade tóxica e custo de investigação; um feed global perde o contexto esportivo | Chat autenticado por partida, denúncia por mensagem, silenciamento e moderação administrativa |
| Usuários criarem mercados/odds da competição oficial | Daria variedade e reduziria trabalho da equipe | Abre manipulação, mercados ofensivos e inconsistência de liquidação; conflito de interesse fica difícil de controlar | Somente administradores autorizados propõem/publicam; usuários podem sugerir pautas fora do fluxo de aposta |
| Rankings sem política de empate, elegibilidade e correção | É mais rápido ordenar só por saldo | Empates instáveis, contas inelegíveis e correções retroativas destroem confiança | Regra versionada, critérios públicos, snapshot e recálculo auditável |
| Aplicativos nativos no primeiro lançamento | Parecem mais “profissionais” e habilitam push | Duplicam superfície de entrega antes de validar retenção e operação | Web responsiva instalável; avaliar nativo apenas com uso recorrente comprovado |
| Branding que sugira produto oficial da UFU | Emprestaria confiança institucional | Cria risco reputacional e confunde usuários sobre fonte, endosso e responsabilidade | Declaração clara de independência e identidade visual própria |
| Mecânicas de cassino e pressão artificial | Confete, quase-acerto, contagem agressiva e urgência podem elevar sessões | Contradizem competição saudável e tornam ambígua a promessa “sem dinheiro real” | Feedback sóbrio, regras claras, limites operacionais e foco em conhecimento/torcida |

## Dependências de Funcionalidades

```text
[Identidade e perfis]
    ├──> [Verificação UFU] ──> [Elegibilidade para palpites internos]
    │                           └──> [Ranking UFU]
    ├──> [Pools privados] ────────> [Ranking de amigos]
    └──> [Chat] ──────────────────> [Denúncia, silenciamento e moderação]

[Modelo esportivo extensível]
    ├──> [Equipes e atletas] ──> [Páginas de equipes]
    │                           └──> [Bloqueio de atleta na própria equipe]
    └──> [Partidas e horários] ──> [Mercados/odds]
                                 ├──> [Fechamento pré-jogo]
                                 └──> [Chat por partida]

[Histórico esportivo confiável] ──> [Odd sugerida]
                                      └──> [Revisão/publicação administrativa]
                                            └──> [Versão de odd]
                                                  └──> [Bilhete com odd aceita]

[Livro-razão de créditos] ──> [Reserva/débito do palpite]
[Resultado oficial/admin] ──> [Liquidação idempotente] ──> [Saldo]
                                                       ├──> [Rankings]
                                                       └──> [Conquistas/estatísticas]

[RBAC + trilha de auditoria]
    ├──> [Mudanças de odds/resultados]
    ├──> [Decisões de verificação]
    ├──> [Conflitos de interesse]
    └──> [Ações de moderação]
```

### Notas de Dependência

- **A exploração pública vem antes da verificação:** visitantes e contas não verificadas devem descobrir jogos, equipes e resultados; a verificação só bloqueia ações universitárias restritas.
- **A verificação não pode depender de conservar o documento:** a decisão deve gerar um registro mínimo e auditável; o arquivo temporário segue para exclusão após análise.
- **O bilhete depende de uma versão publicada de odd:** nunca referenciar apenas “a odd atual”, pois ela pode mudar entre a exibição e a confirmação.
- **Fechamento e saldo precisam ser atômicos:** a última aposta antes do prazo não pode passar do fechamento nem gastar o mesmo saldo duas vezes.
- **Ranking depende da liquidação, não do resultado bruto:** isso permite tratar cancelamento, estorno e correção de forma consistente.
- **Conquistas dependem do mesmo fato liquidado do ranking:** não manter uma segunda lógica de vitória/derrota.
- **Chat depende de capacidade real de moderação:** denúncia sem fila, contexto, decisão e auditoria cria falsa sensação de segurança.
- **Notificações dependem de eventos de domínio confiáveis:** só adicionar depois que fechamento, resultado, liquidação e mensagens tiverem estados estáveis.
- **Contestação formal depende de política de correção e snapshots:** o MVP pode oferecer suporte operacional; janela, evidência e decisão formal entram na v1.x.
- **Mercados multicompetidor não são simples configuração:** exigem modelo e liquidação próprios e pertencem à v2.

## Definição do MVP

O projeto já decidiu que o primeiro lançamento será **social**, e não apenas um protótipo de bilhete. Assim, chat, pools privados, páginas de equipes, rankings e conquistas permanecem no MVP. A redução de escopo deve acontecer no número de edições, modalidades e mercados, não removendo esses pilares.

### Lançar com — MVP/v1

- [ ] **Identidade e descoberta pública:** cadastro, autenticação, apelido, equipe favorita, agenda, filtros e estados claros.
- [ ] **Piloto esportivo controlado:** uma edição das Olimpíadas UFU, somente modalidades de confronto direto com dados confiáveis, páginas de equipes e resultados.
- [ ] **Operação assistida:** console para competições, equipes, atletas, partidas, mercados, odds e resultados, com rascunho/publicação e papéis restritos.
- [ ] **Verificação UFU privada:** upload temporário, revisão manual, uma conta por matrícula, validade/expiração e exclusão do comprovante.
- [ ] **Economia fechada:** distribuição de créditos sem compra/transferência/conversão, livro-razão e histórico compreensível.
- [ ] **Palpite simples pré-jogo:** valor, odd, retorno potencial, confirmação, odd aceita imutável, fechamento no servidor e palpites alheios ocultos.
- [ ] **Odds confiáveis:** sugestão baseada em histórico/forma/adversário, fallback neutro/manual, revisão administrativa e histórico de mudanças.
- [ ] **Liquidação completa:** vitória/derrota, empate quando aplicável, cancelamento/adiamento, estorno, correção auditável e idempotência.
- [ ] **Competição social:** ranking geral, ranking UFU, pools privados, histórico pessoal, critérios de desempate e conquistas aprovadas.
- [ ] **Chat seguro por partida:** texto, denúncia por mensagem, silenciamento, remoção/moderação e política comunitária.
- [ ] **Integridade:** bloqueios para atletas e administradores, RBAC e trilha de auditoria para ações sensíveis.
- [ ] **Entrega web responsiva:** celular e desktop, acessibilidade básica, estados de erro/carregamento e textos que declarem independência da UFU e ausência de valor financeiro.

**Corte operacional recomendado:** ativar poucos mercados de resultado simples por partida. Se uma modalidade não tiver tabela, horário, equipes e regra de resultado confiáveis, ela aparece no máximo como conteúdo informativo, não como evento elegível para palpite.

### Adicionar após validação — v1.x

- [ ] **Notificações opt-in:** lembrete de fechamento, mudança material/cancelamento, liquidação, convite e menção; adicionar quando eventos e preferências estiverem estáveis.
- [ ] **Contestação formal de resultado/liquidação:** prazo, evidência, estado “em revisão”, decisão registrada, snapshot do ranking e comunicação aos afetados.
- [ ] **Estatísticas de desempenho:** por modalidade, período, rodada e tipo de seleção, com metodologia pública.
- [ ] **Sabedoria da torcida pós-fechamento:** distribuição agregada de escolhas/volume, com limiar mínimo de participantes.
- [ ] **Reações e respostas no chat:** somente depois de validar volume, moderação e ergonomia do chat textual.
- [ ] **Torcedômetro e duelos entre torcidas:** sem créditos transferíveis nem prêmio financeiro.
- [ ] **Ferramentas operacionais de importação e revisão em lote:** priorizar se cadastro manual se tornar gargalo mensurável.
- [ ] **Administração avançada dos pools:** coadministrador, aprovação de entrada, arquivo/reinício e histórico por edição; sem customização financeira.

### Consideração futura — v2+

- [ ] **Mercados para provas multicompetidor:** atletismo, natação e outros formatos somente após pesquisa específica de modelagem, empate, desistência e liquidação.
- [ ] **Arquivo permanente de edições:** medalhas, recordes, rivalidades e histórico de grupos entre temporadas.
- [ ] **Novas competições, campi ou universidades:** isolamento de dados, regras e elegibilidade por evento/organização.
- [ ] **Integrações com fontes oficiais:** apenas com acordo, qualidade, SLA e proveniência definidos; manter fallback administrativo.
- [ ] **Quizzes e votações não financeiros:** se dados mostrarem demanda de engajamento fora das janelas de palpite.
- [ ] **Aplicativos nativos:** somente se retenção, notificações e uso recorrente justificarem duas novas superfícies de produto.

**Continuam fora do escopo em qualquer horizonte atual:** dinheiro real, compra/saque/transferência de créditos, apostas ao vivo e apostas múltiplas.

## Matriz de Priorização

| Capacidade | Valor para usuário | Custo | Risco se ausente | Prioridade | Horizonte |
|---|---:|---:|---:|---:|---|
| Agenda, equipes e páginas de partida | ALTO | MÉDIO | ALTO | P1 | MVP |
| Cadastro/perfil/equipe favorita | ALTO | MÉDIO | ALTO | P1 | MVP |
| Verificação UFU e exclusão do documento | ALTO | ALTO | ALTO | P1 | MVP |
| Console operacional e RBAC | ALTO | ALTO | ALTO | P1 | MVP |
| Créditos e livro-razão | ALTO | ALTO | ALTO | P1 | MVP |
| Bilhete simples, fechamento e ocultação | ALTO | ALTO | ALTO | P1 | MVP |
| Odd aceita, histórico e revisão | ALTO | ALTO | ALTO | P1 | MVP |
| Resultado, liquidação e estorno | ALTO | ALTO | ALTO | P1 | MVP |
| Rankings geral/UFU/amigos | ALTO | ALTO | ALTO | P1 | MVP |
| Pool privado e convite | ALTO | MÉDIO | ALTO | P1 | MVP |
| Chat com denúncia/silenciamento/moderação | ALTO | ALTO | ALTO | P1 | MVP |
| Conquistas aprovadas | MÉDIO | MÉDIO | MÉDIO | P1 | MVP |
| Auditoria e conflitos de interesse | ALTO | ALTO | ALTO | P1 | MVP |
| Web responsiva e acessibilidade básica | ALTO | MÉDIO | ALTO | P1 | MVP |
| Notificações opt-in | ALTO | MÉDIO | BAIXO no piloto | P2 | v1.x |
| Contestação formal | MÉDIO | ALTO | MÉDIO após escala | P2 | v1.x |
| Estatísticas e agregados pós-fechamento | MÉDIO | MÉDIO | BAIXO | P2 | v1.x |
| Torcedômetro/duelos de torcida | MÉDIO | MÉDIO | BAIXO | P2 | v1.x |
| Importação/automação operacional | MÉDIO | ALTO | Depende do volume | P2 | v1.x |
| Mercados multicompetidor | MÉDIO | ALTO | BAIXO no piloto | P3 | v2 |
| Memória entre edições e expansão multicampi | MÉDIO | ALTO | BAIXO | P3 | v2 |
| Aplicativos nativos | BAIXO antes da validação | ALTO | BAIXO | P3 | v2 |

**Chave:** P1 = obrigatório para o lançamento aprovado; P2 = adicionar após validar ciclo e operação; P3 = só após evidência de demanda e capacidade.

## Análise de Produtos de Referência

### Mapa competitivo

| Produto | Categoria e capacidades observadas | Limite em relação ao BETUFU | Lição aplicável | Confiança |
|---|---|---|---|---|
| [BeTeam](https://beteamapp.com/) | Concorrente conceitual mais próximo: previsão social sem depósitos/dinheiro/prêmio, ligas privadas, convite por link, bloqueio no início, pontuação automática, ranking, histórico, chat e reações | Focado em competições esportivas amplas; não evidencia verificação universitária, economia por odds auditáveis ou operação de torneio local | O núcleo deve caber em três verbos: convidar, palpitar, subir no ranking. Chat embutido e histórico transparente já são expectativa em 2026 | ALTA |
| [Kicktipp](https://www.kicktipp.com/info/professional/functions/) | Comunidades configuráveis, ranking individual/equipe/grupo, estatísticas, prazo e visibilidade configuráveis, odds/tendências, calendário e log administrativo | É um sistema de pontuação de palpites, não uma plataforma centrada em créditos e vínculo universitário | Regras, prazo, visibilidade, desempate e alterações administrativas precisam ser explícitos e auditáveis | ALTA |
| [Superbru](https://www.superbru.com/about) | Predictor gratuito, muitos esportes, pools, comunidade, previsões agregadas; clubes persistem entre torneios com histórico, medalhas e banter | Amplitude global dilui a identidade local; monetiza por anúncios/premium e não evidencia verificação universitária | Pools criam aquisição; clubes/memória entre edições criam retenção. Deferir persistência avançada para v2 | ALTA |
| [RunYourPool](https://help.runyourpool.com/en/articles/6494229-runyourpool-guide-dashboard-connections) | Pools majoritariamente privados, comissão/administrador, convites, pedidos pendentes, message board, múltiplos formatos, relatórios e histórico | Muitos controles e formatos aumentam carga cognitiva; serviço cobra hospedagem do pool e não é uma experiência universitária local | O fluxo do organizador e os relatórios importam tanto quanto a tela do jogador; não copiar a explosão de configurações no MVP | ALTA |
| [Inter USP 2026](https://apps.apple.com/br/app/inter-usp-2026/id6769940648) | App oficial de campeonato universitário: tabelas por modalidade, resultados, ginásios, programação, notícias, notificações, equipe de torcida e torcedômetro | Não é jogo de previsão/pool segundo a página analisada | A camada universitária exige modalidade, local, programação e torcida; torcedômetro é bom candidato pós-MVP | ALTA |
| [APP Esportivo](https://appesportivo.com.br/) | Gestão de campeonatos amadores/universitários: equipes, atletas, tabela, classificação, súmula, resultados e formas de disputa | Orientado à organização da competição, não à previsão social | O dado esportivo é produto. Sem tabela, equipe e resultado confiáveis, odds e liquidação não devem ser ativadas | ALTA |
| [IMLeagues — referência institucional](https://www.uww.edu/rec-sports/programs/intramural-sports/im-leagues) | Roster, agenda, placares, classificação, página de equipe, regras de elegibilidade e processo de protesto em esportes intramuros | Ferramenta operacional universitária, não jogo de previsão | Vínculo, elenco, congelamento de elegibilidade e contestação têm ciclos próprios; contestação formal deve vir depois do fluxo básico | MÉDIA-ALTA |
| [HUDDLE](https://huddle.sport/en/features) | Plataforma de engajamento com palpites pré-jogo, apuração automática, regras configuráveis, ranking sazonal, quizzes, votação e carteira de tokens | Inclui comércio/recompensas e grande escopo white-label incompatível com o piloto | Ranking transversal, quizzes e votação são possibilidades futuras; transferências, cupons e recompensas não pertencem à economia do BETUFU | ALTA |

### Matriz comparativa de funcionalidades

**Legenda:** ● = explicitamente documentado na fonte citada; ◐ = recurso adjacente/parcial; — = não evidenciado nas páginas analisadas (não significa necessariamente que o produto não possua).

| Funcionalidade | BeTeam | Kicktipp | Superbru | RunYourPool | Inter USP / APP Esportivo | BETUFU recomendado |
|---|:---:|:---:|:---:|:---:|:---:|---|
| Competição sem dinheiro real | ● | ◐ | ◐ gratuito, sem conclusão sobre prêmios | ◐ | n/a | ● Créditos sem valor, sem prêmio ou fluxo financeiro |
| Pool/comunidade privada | ● | ● | ● | ● | — | ● Disputas entre amigos com convite simples |
| Bloqueio antes do evento | ● | ● | ◐ | ● | n/a | ● Fechamento no servidor antes do início |
| Ocultação de palpites até o prazo | ◐ | ● | — | ◐ | n/a | ● Regra obrigatória |
| Apuração e ranking | ● | ● | ● | ● | ● classificação esportiva | ● Geral, UFU e amigos, com desempate público |
| Histórico pessoal/da comunidade | ● | ● log admin | ● clubes | ● | ◐ resultados | ● Bilhetes, saldo, odds e auditoria |
| Chat/message board | ● | ◐ | ● | ● | — | ● Por partida, com denúncia e silenciamento |
| Agenda, local, equipe e elenco universitário | — | ◐ calendário | ◐ | ◐ | ● | ● Conteúdo essencial, não decorativo |
| Verificação de vínculo estudantil | — | — | — | — | ◐ elegibilidade institucional | ● Manual, temporária e com expiração |
| Odd/multiplicador no palpite | — pontuação | ● modelo próprio | ◐ | ◐ spreads em formatos | ● Crédito × odd, com retorno potencial |
| Histórico público de mudanças da odd | — | ◐ log/regras | — | — | n/a | ● Diferenciador de confiança no MVP |
| Impedimento por conflito de interesse | — | — | — | — | ◐ regras de elenco | ● Atleta/equipe e admin/evento |
| Operação de competição local | — | ◐ league manager | — | ◐ commissioner | ● | ● Console assistido e auditável |
| Conquistas/status não financeiro | ◐ posições | ● vencedor da rodada | ● medalhas | ◐ | ◐ torcida | ● Sequência, azarão, rodada e apoio à equipe |

## Implicações para Descoberta e Experiência

1. **A home deve priorizar “próximas partidas” e “o que fecha em breve”, não saldo isolado.** O contexto esportivo precede o bilhete.
2. **Cada ação sensível precisa explicar elegibilidade.** Conta não verificada vê o conteúdo e um caminho claro para verificação; atleta/admin impedido vê o motivo, sem revelar dados internos.
3. **A confirmação do palpite deve ser uma prova resumida:** seleção, créditos, odd, retorno potencial, horário de fechamento e regra aplicável.
4. **Após o fechamento, a página muda de função:** libera distribuição agregada/palpites permitidos, mantém chat e acompanha resultado; não aceita novas seleções.
5. **Após a liquidação, explicar o movimento de saldo:** resultado usado, cálculo, créditos devolvidos/ganhos e link para regra/histórico da odd.
6. **Rankings devem mostrar recorte e atualização:** geral vs. UFU vs. grupo, edição/modalidade/período, critério de desempate e data de cálculo.
7. **O chat deve nascer com segurança visível:** denunciar na própria mensagem, silenciar sem confronto e regras curtas junto ao composer.
8. **Estados sem dados são honestos e úteis:** quando o histórico for insuficiente, declarar estimativa neutra/manual; nunca inventar precisão.

## Riscos de Validação do Produto

| Hipótese a validar | Sinal positivo | Sinal de falha | Consequência para roadmap |
|---|---|---|---|
| Conteúdo local aumenta frequência de visita | Usuários consultam agenda/equipes mesmo sem palpitar | Acesso só ocorre no momento de apostar | Reforçar dados, páginas de equipe e cobertura antes de ampliar mercados |
| Verificação manual é aceitável | Boa conversão e tempo de decisão curto | Abandono alto ou fila operacional longa | Pesquisar método alternativo; não guardar mais dados para “resolver” o problema |
| Odds são compreendidas como mecânica recreativa | Baixa taxa de dúvidas/erros e boa leitura do retorno potencial | Confusão com dinheiro real ou cálculo recorrente incorreto | Simplificar linguagem e mercados; revisar UX antes de adicionar funcionalidades |
| Pools privados geram aquisição | Convites convertem e grupos seguem ativos | Grupos criados sem novos membros | Melhorar convite/onboarding antes de customizações avançadas |
| Chat por partida aumenta pertencimento sem sobrecarregar moderação | Participação saudável, denúncias resolvíveis | Abuso, spam ou fila crescente | Investir em limites/ferramentas antes de reações e recursos sociais adicionais |
| Conquistas sustentam motivação não financeira | Usuários retornam por sequência/posição/equipe | Só saldo/ranking absoluto gera interesse | Ajustar metas e feedback; não introduzir prêmio de valor |
| Operação assistida suporta o piloto | Jogos/odds/resultados publicados no prazo, poucas correções | Atrasos e retrabalho frequentes | Priorizar importação/revisão em lote na v1.x antes de expandir modalidades |

## Fontes

### Produtos de previsão e pools

- [BeTeam — página oficial do produto](https://beteamapp.com/) — ligas privadas, convites, fechamento, ranking, histórico, chat/reação e declaração explícita de ausência de dinheiro real. **Confiança: ALTA para capacidades declaradas; adoção e escala não foram verificadas.**
- [Kicktipp — funções do jogo de previsão](https://www.kicktipp.com/info/professional/functions/) — rankings, regras, prazo, visibilidade, estatísticas, calendário e log administrativo. **Confiança: ALTA.**
- [Kicktipp — regra vigente de visibilidade e prazo](https://www.kicktipp.com/the-predictor/rules) — palpites visíveis somente após o prazo e critérios de desempate. **Confiança: ALTA.**
- [Kicktipp — ranking de equipes](https://www.kicktipp.com/info/service/help/2/215) — convites, equipes, liderança e regra de média entre grupos de tamanhos diferentes. **Confiança: ALTA.**
- [Superbru — descrição e números da plataforma](https://www.superbru.com/about) — predictor gratuito, 80+ torneios/10 esportes, pools e previsões agregadas. **Confiança: ALTA.**
- [Superbru — clubes](https://www.superbru.com/home/about_clubs.php) — comunidade, banter e memória de desempenho entre torneios. **Confiança: ALTA.**
- [Superbru — termos de uso](https://www.superbru.com/policies/terms-of-use) — criação/gestão de pools, mensagens e compartilhamento entre membros. **Confiança: ALTA.**
- [RunYourPool — dashboard e conexões](https://help.runyourpool.com/en/articles/6494229-runyourpool-guide-dashboard-connections) — pools, solicitações pendentes, comissão, message board e convites/conexões. **Confiança: ALTA.**
- [RunYourPool — convites](https://help.runyourpool.com/en/articles/6500215-how-do-i-invite-members-after-i-created-my-pool) — link compartilhável, e-mail e aceite/recusa. **Confiança: ALTA.**
- [RunYourPool — edição antes do prazo](https://help.runyourpool.com/en/articles/5547009-can-i-modify-my-picks-once-i-ve-submitted-them) — mudanças permitidas até o deadline definido. **Confiança: ALTA.**
- [RunYourPool — pool head-to-head](https://help.runyourpool.com/en/articles/5686973-about-march-madness-head-to-head-pools) — configuração do comissário, scoring/standings e message board privado. **Confiança: ALTA.**

### Esporte universitário, gestão e engajamento

- [Inter USP 2026 — App Store](https://apps.apple.com/br/app/inter-usp-2026/id6769940648) — tabela por modalidade, resultados, ginásios, programação, notícias, notificações, time de torcida e torcedômetro. **Confiança: ALTA para a descrição publicada; sem evidência de adoção suficiente para avaliar tração.**
- [APP Esportivo — recursos oficiais](https://appesportivo.com.br/) — equipes, atletas, tabela, classificação, súmula e resultados para torneios, inclusive universitários. **Confiança: ALTA.**
- [IMLeagues na University of Wisconsin–Whitewater](https://www.uww.edu/rec-sports/programs/intramural-sports/im-leagues) — rosters, agenda, placares, classificação, elegibilidade e protesto. **Confiança: MÉDIA-ALTA; fonte institucional usuária, não documentação central do fornecedor.**
- [HUDDLE — recursos oficiais](https://huddle.sport/en/features) — palpites pré-jogo, verificação automática, regras, ranking sazonal e módulos futuros de engajamento. **Confiança: ALTA para capacidades declaradas; não foi validada adoção.**
- [ESPN Tournament Challenge 2026 — App Store](https://apps.apple.com/us/app/espn-tournament-challenge/id359891723) — grupos, countdown de fechamento, lembrete de entradas incompletas, pontos visíveis e análises; o prêmio financeiro torna o benchmark inadequado como modelo econômico. **Confiança: ALTA.**

### Privacidade, integridade e limite econômico

- [Lei Geral de Proteção de Dados — Lei 13.709/2018, texto compilado](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm) — princípios de finalidade, necessidade, transparência e segurança; término do tratamento e eliminação. **Confiança: ALTA.**
- [Lei 14.790/2023 — apostas de quota fixa](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2023/lei/l14790.htm) — definição legal de aposta como valor colocado em risco na expectativa de prêmio e regime de apostas esportivas de quota fixa. A recomendação aqui é de escopo de produto, não parecer jurídico. **Confiança: ALTA para o texto legal.**
- [Secretaria de Prêmios e Apostas — legislação vigente](https://www.gov.br/fazenda/pt-br/composicao/orgaos/secretaria-de-premios-e-apostas/apostas-de-quota-fixa/legislacao/apostas) — panorama regulatório federal atualizado em 2026. **Confiança: ALTA.**

## Lacunas e Pesquisa Posterior

- Não foi encontrada uma plataforma dominante que reúna, ao mesmo tempo, previsão por odds sem dinheiro real, verificação universitária, conteúdo de competição local e chat por partida. Isso sustenta a diferenciação, mas também significa que a combinação precisa ser testada, não presumida.
- Não há evidência pública suficiente sobre volume, retenção ou eficácia do Inter USP 2026; seus recursos servem como referência funcional, não prova de demanda quantitativa.
- A métrica exata dos rankings (saldo, lucro virtual, retorno percentual, taxa de acerto ou composição) exige pesquisa específica e simulação contra estratégias de alto risco antes da implementação.
- As políticas de partida adiada/cancelada, resultado corrigido e empate precisam ser definidas por modalidade antes de cada uma ser ativada.
- O cálculo de odds com poucos dados e os mercados multicompetidor exigem pesquisa de fase própria; não assumir que o método de confrontos diretos se generaliza.
- A escolha da hipótese legal, retenção mínima e técnica de prevenção de matrícula duplicada requer revisão de privacidade/jurídica antes do lançamento; o comprovante não deve permanecer armazenado por conveniência operacional.
- A moderação precisa de capacidade dimensionada ao volume real e de política comunitária em português; automação pode ser considerada depois, sem substituir revisão humana em casos relevantes.

---
*Pesquisa de funcionalidades para: BETUFU — jogo social universitário de previsões esportivas sem dinheiro real*  
*Pesquisado em: 2026-08-01*
