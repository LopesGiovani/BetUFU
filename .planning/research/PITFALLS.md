# Pesquisa de Riscos e Armadilhas

**Domínio:** plataforma social de palpites esportivos universitários com créditos virtuais
**Pesquisado em:** 2026-08-01
**Confiança:** ALTA para segurança, privacidade e consistência transacional; MÉDIA para enquadramento jurídico e uso de marca, que exigem validação profissional específica

## Armadilhas Críticas

### Armadilha 1: deixar a economia virtual adquirir valor real

**O que dá errado:**
Créditos inicialmente recreativos passam a ser comprados, transferidos, trocados por prêmios ou negociados informalmente. O produto muda de natureza sem que arquitetura, termos, controles ou análise jurídica acompanhem a mudança.

**Por que acontece:**
Monetização e premiações parecem extensões pequenas do MVP, mas removem a fronteira de segurança que sustenta o escopo inicial. A Lei nº 14.790/2023 define aposta como colocar valor em risco à espera de prêmio e quota fixa por unidade de moeda nacional; a BETUFU foi desenhada sem esses elementos monetários. Isso reduz o risco, mas não é, por si só, uma conclusão oficial de não incidência, e o marco brasileiro continuou recebendo normas em 2026.

**Como evitar:**
Codificar e testar invariantes de produto e banco: pontos são concedidos gratuitamente pelo sistema, não são compráveis, transferíveis, negociáveis, resgatáveis ou conversíveis; não dão acesso a dinheiro, bens, descontos, sorteios ou prêmio patrocinado de valor econômico. Na interface, usar “palpites” e “pontos recreativos”, sem `R$`, depósito, saque ou promessa de ganho. Termos e marketing devem refletir a operação real — trocar o nome da unidade não altera sua substância. Antes do piloto público, validar o modelo, a política etária e a comunicação com assessoria jurídica brasileira; qualquer prêmio ou monetização futura é uma iniciativa separada, não uma flag.

**Sinais de alerta:**
Pedidos por PIX, loja, resgate, vale-presente, desconto, sorteio, prêmio patrocinado, transferência entre usuários, mercado paralelo ou mensagens que prometem ganhos; matrícula verificada sendo tratada como prova de maioridade.

**Fase para tratar:**
Fundação do produto e requisitos; revalidar em todo milestone.

---

### Armadilha 2: aparentar vínculo oficial ou usar a Marca UFU sem validação

**O que dá errado:**
Nome, logotipo, cores, textos ou domínios levam usuários a acreditar que a BETUFU é operada ou endossada pela universidade. Isso cria risco de confiança, retirada de conteúdo e necessidade de rebranding tardio.

**Por que acontece:**
O produto depende do contexto da UFU e a identidade oficial parece o caminho mais rápido para comunicá-lo. Porém, a página oficial da universidade informa que a Resolução CONSUN nº 04/2011 regulamenta a utilização e aplicação da Marca UFU; essa página não equivale a autorização para uso por terceiro.

**Como evitar:**
Usar identidade visual própria; não importar logotipo, assinatura ou ativos oficiais sem autorização escrita; inventariar também logos de olimpíadas, atléticas, equipes e imagens de atletas, porque publicação pública não significa licença de reutilização. Exibir aviso de independência em cadastro, rodapé e páginas de competição, sem presumir que o aviso sane uma identidade confusa. Submeter nome “BETUFU”, domínio, perfis, textos e identidade a revisão específica de marca antes do lançamento público.

**Sinais de alerta:**
Logo oficial no protótipo, texto “plataforma da UFU”, domínio que parece institucional, selo “oficial” ou usuários perguntando qual setor da universidade mantém o produto.

**Fase para tratar:**
Fundação de marca/UI e preparação de lançamento.

---

### Armadilha 3: transformar o comprovante de matrícula em arquivo permanente

**O que dá errado:**
Comprovantes ficam em URLs públicas, backups, logs, ambientes de teste ou contas de administradores por tempo indefinido. Um incidente expõe nome, matrícula, curso e outros dados desnecessários.

**Por que acontece:**
Upload é tratado como simples anexo, sem ciclo de vida; exclusão lógica no banco não elimina o objeto, versões ou cópias; administradores baixam arquivos para computadores pessoais.

**Como evitar:**
Antes do upload, documentar controlador/operadores, finalidade, hipótese legal aplicável, campos necessários, destinatários, retenção e canal do titular — consentimento não deve ser escolhido automaticamente. Aceitar somente conta autenticada; limitar a PDF/imagem indispensável; validar extensão, assinatura real, tamanho e conteúdo; renomear; varrer; e manter em bucket privado isolado, sem ACL pública. Dar ao revisor acesso temporário, individual, auditado e com MFA, sem download rotineiro. Após a decisão, eliminar objeto, versões, miniaturas e cache por job verificável; backups devem expirar por política documentada. Conservar apenas status, datas, revisor e um HMAC normalizado da matrícula com segredo fora do banco para unicidade — nunca matrícula crua ou hash simples de baixa entropia. Preparar resposta a incidente antes do primeiro documento real.

**Sinais de alerta:**
URL funciona sem autenticação, arquivo aparece em log, matrícula crua permanece no banco, `deleted_at` muda mas o objeto continua acessível, ausência de métrica/fila de deleção, administrador compartilha ou baixa o documento, backup não tem expiração ou o aviso de privacidade não descreve esse tratamento.

**Fase para tratar:**
Identidade, verificação e privacidade — antes de aceitar o primeiro upload real.

---

### Armadilha 4: saldo mutável sem ledger e liquidação não idempotente

**O que dá errado:**
Duas apostas simultâneas gastam o mesmo saldo; uma fila repetida paga o prêmio duas vezes; cancelar ou corrigir uma partida perde a origem do crédito; o total do sistema deixa de reconciliar.

**Por que acontece:**
O MVP mantém apenas `users.balance` e executa “ler saldo, verificar, atualizar” em operações separadas. Jobs de liquidação são assumidos como exatamente uma vez, embora filas e retries normalmente sejam pelo menos uma vez.

**Como evitar:**
Usar ledger append-only em unidades inteiras, com cada débito/crédito ligado a usuário, causa e identificador único da operação; correções são lançamentos compensatórios, nunca edição. Executar aposta, débito e snapshot da odd na mesma transação, com lock ou atualização condicional do saldo. A chave de idempotência deve ser estável por usuário/operação, guardar fingerprint do payload e resposta, e rejeitar reutilização com conteúdo diferente. Liquidação, estorno e reliquidação precisam de restrições únicas por aposta e revisão do resultado; retries repetem a transação inteira. Saldo em cache é apenas projeção reconciliável. Não usar ponto flutuante: créditos são inteiros e odds usam decimal explícito.

**Sinais de alerta:**
Saldo negativo, `UPDATE balance = ...` espalhado pelo código, lançamento editável, chave nova a cada retry, mesma chave aceita com payload distinto, job sem unicidade por aposta/revisão, liquidação manual que edita saldo ou impossibilidade de explicar cada lançamento.

**Fase para tratar:**
Motor de créditos e apostas; testar concorrência antes da UI completa.

---

### Armadilha 5: odds que não podem ser reproduzidas

**O que dá errado:**
Uma mudança administrativa altera apostas antigas, o retorno potencial exibido diverge da liquidação ou ninguém consegue explicar por que uma odd foi publicada.

**Por que acontece:**
Existe uma única odd atual por mercado e o sistema recalcula retornos consultando esse valor mutável. O algoritmo e os dados de entrada também mudam sem versão.

**Como evitar:**
Tornar cada publicação imutável e versioná-la com vigência, origem (manual/modelo), algoritmo, versão do modelo, dados de entrada resumidos, precisão/arredondamento, autor e aprovador. O cliente envia `odds_version_id`; dentro da transação, o servidor confirma que essa versão ainda está publicada, vigente e com mercado aberto. Se estiver obsoleta, rejeita e pede nova confirmação — nunca substitui silenciosamente. A aposta referencia a versão aceita e grava odd, retorno potencial e regra de arredondamento; alterações afetam só novas apostas. Exibir histórico público suficiente para explicar mudanças sem revelar dados pessoais ou controles internos sensíveis.

**Sinais de alerta:**
Tabela de mercado contém somente `odds`, edição sobrescreve valores, endpoint recebe apenas a seleção sem `odds_version_id`, versão obsoleta é trocada automaticamente, aposta não referencia versão ou retorno é recalculado no momento da liquidação.

**Fase para tratar:**
Modelo esportivo e motor de odds, antes de liberar apostas.

---

### Armadilha 6: misturar ciclo da partida, abertura de palpites e liquidação

**O que dá errado:**
Usuários apostam após o início real, partidas adiadas fecham na hora errada, um evento cancelado é tratado como mercado anulado ou uma correção de resultado paga e remove créditos repetidamente.

**Por que acontece:**
Um único campo `status` tenta representar a partida, a janela de palpites e a liquidação; o cliente decide se o botão está ativo; horário é armazenado sem timezone; e cron é considerado a única barreira.

**Como evitar:**
Separar máquinas de estado: evento (`draft/scheduled/postponed/cancelled/in_progress/finished`), palpites (`closed/open/suspended/locked`) e liquidação (`pending/provisional/settled/voided/reversed`). Definir uma matriz de transições e efeitos, inclusive WO, adiamento, cancelamento e correção. Armazenar instantes em UTC, exibir em `America/Sao_Paulo` e validar `locks_at` no servidor, na mesma transação da aposta. Cada comando verifica pré-condição, registra fonte/autor/motivo e é idempotente; correção gera nova revisão e lançamentos compensatórios. Cron ajuda a fechar, mas a transação é a barreira final; deve existir suspensão emergencial sem apagar histórico.

**Sinais de alerta:**
Um único status para tudo, comparação com hora local do dispositivo, status editado livremente, ausência de `locks_at`, cron que fecha tarde sem validação no servidor, adiamento que reabre mercado automaticamente ou resultado corrigido por edição direta.

**Fase para tratar:**
Administração de partidas e motor de apostas.

---

### Armadilha 7: confundir matrícula verificada com integridade esportiva

**O que dá errado:**
Atleta, técnico, organizador ou administrador aposta em evento que pode influenciar; conta verificada usa elenco desatualizado; um único administrador cria odds e liquida o próprio mercado sem revisão.

**Por que acontece:**
Verificação de vínculo é tratada como prova de ausência de conflito. Relações com equipes mudam por edição, funções administrativas mudam por evento e os controles ficam apenas nos termos de uso. A Lei nº 14.790/2023 e a PNPEMR de 2026 são referências fortes de integridade esportiva, mas sua citação aqui é benchmark de risco, não conclusão de aplicabilidade à BETUFU.

**Como evitar:**
Definir política de conflito por papel e evento; manter vínculos de atleta/técnico/equipe e atribuições administrativas com vigência por edição. No servidor, negar palpite no próprio time e impedir o administrador responsável de participar do evento que opera; registrar qual vínculo e regra produziram a decisão. Separar criação, aprovação de odds e liquidação em operações de alto impacto ou, enquanto a equipe for pequena, exigir segunda confirmação e auditoria reforçada. Toda exceção precisa de motivo e revisão; mudanças de elenco não reescrevem a evidência da aposta. Tratar o controle como redução de risco, não garantia absoluta.

**Sinais de alerta:**
Elenco sem validade temporal, papel `admin` irrestrito, atribuição administrativa sem escopo de evento, ação sem autor/motivo, conflito verificado apenas na UI, vínculo alterado retroativamente ou exclusão de logs.

**Fase para tratar:**
RBAC, cadastros esportivos e auditoria; verificar novamente antes do piloto.

---

### Armadilha 8: lançar chat sem modelo de abuso, evidência e retenção

**O que dá errado:**
Spam, assédio, conteúdo ofensivo, flood, impersonação e injeção tornam a comunidade insegura; denúncias somem com a mensagem; bloqueios não têm efeito imediato. Se houver WebSocket, uma conexão autenticada pode receber permissões amplas demais ou continuar ativa após logout/banimento.

**Por que acontece:**
Chat parece apenas uma tabela de mensagens. O transporte é confundido com a política de moderação; no WebSocket, logs HTTP comuns não registram o tráfego posterior ao upgrade e autorização costuma ser checada só na conexão.

**Como evitar:**
Publicar regras comunitárias e fluxo de sanções; permitir denunciar, silenciar, bloquear e remover desde o lançamento. Guardar estado da denúncia, mensagem referenciada ou evidência mínima restrita, autor/moderador, motivo e timestamps; definir retenção do conteúdo e da auditoria. Autorizar cada ação/canal, validar schema/tamanho, armazenar texto e codificar a saída, aplicar limites por usuário/IP/canal e impedir impersonação. Polling por cursor pode servir ao piloto se limitado; se houver WebSocket, validar `Origin`, revalidar sessão/permissão e encerrar conexões revogadas. Registrar violações sem copiar conversas, tokens ou dados pessoais inteiros para logs.

**Sinais de alerta:**
Payload sem limite, HTML arbitrário, nenhum botão/fila de denúncia, mensagem apagada elimina toda evidência, moderador age sem motivo, banimento não bloqueia novos envios ou ausência de métricas de flood.

**Fase para tratar:**
Camada social/chat, depois de autenticação e autorização maduras.

---

### Armadilha 9: ranking injusto por desenho da economia

**O que dá errado:**
Quem entrou primeiro, recebeu mais recargas ou apostou em uma odd posteriormente corrigida domina o ranking; usuários quebrados abandonam a plataforma; amigos não conseguem comparar desempenho.

**Por que acontece:**
Ranking por saldo absoluto é lançado antes de definir temporada, saldo inicial, recargas, empate, apostas anuladas e entrada tardia.

**Como evitar:**
Definir e versionar regras por temporada/edição: população elegível, saldo inicial, reposição, entrada tardia, critérios de desempate, expiração da verificação, estorno e `void`. Calcular a partir de liquidações/ledger, com replay determinístico, e preservar snapshots publicados. Especificar separadamente ranking geral, ranking UFU e grupos privados; durante um piloto só com jogos UFU, deixar claro que geral e UFU podem ter a mesma população de apostadores. Oferecer acurácia, retorno virtual e sequência como métricas nomeadas, sem fórmula opaca. Simular cenários antes do piloto.

**Sinais de alerta:**
Fórmula muda durante a competição, recarga manual discricionária, ranking não registra versão, “geral” não tem população definida, verificação expirada remove histórico, `void` muda posições de forma irreproduzível ou vitória é decidida apenas por volume apostado.

**Fase para tratar:**
Economia e ranking, antes de conquistas e ligas privadas.

---

### Armadilha 10: forçar todas as modalidades no mesmo mercado

**O que dá errado:**
Empate aparece onde não existe, prova com vários atletas é fingida como confrontos binários, desistências e fases eliminatórias liquidam incorretamente.

**Por que acontece:**
O domínio é modelado diretamente como `home_team`, `away_team`, `home_odds`, `away_odds`, sem separar evento, participantes, mercado e resultados.

**Como evitar:**
Criar núcleo genérico de competição/edição/evento/participante/mercado/seleção/resultado, com templates e regras de liquidação versionadas por família: confronto com ou sem empate, classificação/avanço e múltiplos competidores. Testar fixtures de empate, WO, desistência, desclassificação, adiamento e correção. Ativar apenas templates completamente testados; manter mercados multicompetidor de atletismo e natação fora do MVP, mesmo que o cadastro genérico já os represente.

**Sinais de alerta:**
Colunas `home`/`away` obrigatórias em toda entidade, equipe e atleta no mesmo campo polimórfico sem invariantes, condicionais por nome do esporte, resultado sem regra de liquidação versionada ou administradores improvisando mercados.

**Fase para tratar:**
Modelo esportivo, antes do primeiro import de tabela.

## Padrões de Dívida Técnica

| Atalho | Benefício imediato | Custo de longo prazo | Quando é aceitável |
|--------|--------------------|----------------------|--------------------|
| Guardar apenas `balance` | CRUD simples | Saldo irreconciliável e dupla liquidação | Nunca no motor real; somente protótipo descartável |
| Sobrescrever odds | Menos tabelas | Aposta não reproduzível e disputa de confiança | Nunca após aceitar apostas |
| Usar `float` para créditos/odds | Código rápido | Erros de arredondamento e reconciliação | Nunca; usar inteiros/decimal explícito |
| Um papel `admin` com acesso total | RBAC mínimo | Conflitos, erro humano e auditoria fraca | Apenas demonstração local sem dados reais |
| Guardar comprovante junto a assets públicos | Deploy simples | Exposição de dados pessoais e retenção indefinida | Nunca |
| Polling agressivo para chat/ranking | Fácil de entender | Carga, duplicação e UX degradam rapidamente | Polling por cursor, incremental e medido pode servir ao piloto; evitar intervalo curto e histórico completo |
| Recalcular todo ranking em cada request | Sem job/materialização | Consultas caras e inconsistentes | Apenas com dataset pequeno e medição; manter caminho para projeção |
| Condicionais por modalidade espalhadas | Entrega rápida de um esporte | Expansão frágil | Somente atrás de templates/estratégias centralizadas |
| Um `status` para evento, palpites e liquidação | Menos tabelas/transições | Adiamento, `void` e correção ficam ambíguos | Nunca no motor real; separar ciclos desde o modelo inicial |

## Armadilhas de Integração

| Integração | Erro comum | Abordagem correta |
|------------|------------|-------------------|
| Armazenamento de comprovantes | URL pública, cópia local ou `deleted_at` sem exclusão física | Bucket privado, acesso curto/MFA, lifecycle de versões/backups, job de deleção verificável e contrato com operador |
| E-mail institucional | Assumir que domínio prova matrícula ativa | Tratar como sinal; manter status, expiração e fallback manual |
| Publicações oficiais/PDFs | Parser atualiza produção sem revisão ou perde a origem | Importar como rascunho com URL/checksum/data da fonte, validar e publicar com auditoria |
| WebSocket/chat | Escolher WebSocket antes de definir moderação e autorizar só no handshake | Definir política primeiro; validar origem, sessão e permissão por canal/mensagem, limitar tamanho/taxa e revogar conexões |
| Jobs de fechamento/liquidação | Assumir execução exatamente uma vez | Idempotência, restrição única, retry e painel de falhas |
| Cache de odds/ranking | Cache virar fonte de verdade | Banco transacional como autoridade; cache derivável e invalidado por versão |

## Armadilhas de Desempenho

| Armadilha | Sintomas | Prevenção | Quando tende a aparecer |
|-----------|----------|-----------|-------------------------|
| Recalcular ranking completo por visualização | Latência cresce durante evento | Projeção/materialized view atualizada após lançamentos | Centenas de usuários e milhares de apostas |
| N+1 em páginas de equipes | Muitas consultas por elenco/jogo | Consultas agregadas, índices e paginação | Dezenas de equipes com histórico |
| Histórico de chat ilimitado | Memória e payload altos | Paginação por cursor, limites e retenção | Canais movimentados durante partidas |
| Broadcast global de presença/ranking | Muitas conexões e fan-out | Canais por partida/grupo e atualização incremental | Várias partidas simultâneas |
| Auditoria sem índices/particionamento lógico | Painel administrativo lento | Índices por entidade, autor e tempo; retenção definida | Milhões de eventos de log |
| Recalcular odds síncronas em toda leitura | Página instável e cara | Calcular em job/comando administrativo e publicar versão | Histórico esportivo crescente |

## Erros de Segurança

| Erro | Risco | Prevenção |
|------|-------|-----------|
| Confiar em extensão/MIME do comprovante | Malware ou conteúdo inesperado | Allow-list, magic bytes, limite, renomeação, varredura e isolamento |
| Matrícula crua ou hash simples em logs/banco | Exposição, enumeração e reversão por força bruta | Identificador interno, mascaramento e HMAC normalizado com segredo fora do banco para duplicidade |
| Autorização apenas na UI | Apostas/admin/chat acessíveis por API | Política no servidor em cada comando e teste de matriz RBAC |
| Sessão WebSocket não revogada | Usuário bloqueado continua publicando | Revalidação, expiração e encerramento ativo de conexões |
| Auditoria editável pelo mesmo admin | Encobrimento de alterações | Append-only, permissões separadas e export/reconciliação |
| Endpoint de aposta sem limite/idempotência | Spam, duplicação e corrida de saldo | Rate limit, chave de idempotência, transação e restrições únicas |
| Resultado/odd sem dupla confirmação de alto impacto | Erro ou abuso afeta muitos saldos | Preview, confirmação explícita, motivo e revisão para operações sensíveis |
| Admin de verificação/operação sem MFA | Conta comprometida acessa documentos ou altera resultados | MFA, sessão curta para ação sensível, menor privilégio e trilha de auditoria |

## Armadilhas de UX

| Armadilha | Impacto no usuário | Abordagem melhor |
|-----------|--------------------|------------------|
| Visual indistinguível de casa de aposta real | Expectativa de saque e percepção de vínculo/regulação | Reforçar “palpites/pontos recreativos”, saldo sem símbolo monetário, ausência de ganho e independência |
| Odd muda entre bilhete e confirmação | Sensação de manipulação ou aceite involuntário | Mostrar versão/horário e exigir nova confirmação quando a cotação estiver obsoleta |
| Verificação sem prazo/status | Usuário envia repetidamente e desconfia | Estados claros, estimativa operacional, motivo de rejeição e reenvio seguro |
| Saldo chega a zero sem recuperação definida | Abandono | Política previsível por temporada/rodada, sem compra |
| Chat antes da moderação | Primeira experiência tóxica | Regras, denúncia, mute, rate limit e ferramentas admin desde o lançamento |
| Ranking único favorece veteranos | Novos usuários não competem | Temporadas, ligas privadas e métricas complementares |
| Mercado genérico errado para a modalidade | Aposta confusa ou liquidada incorretamente | Templates testados e regras visíveis antes da confirmação |

## Checklist “Parece pronto, mas não está”

- [ ] **Aposta:** duas requisições concorrentes não gastam o mesmo saldo.
- [ ] **Idempotência:** repetir a mesma requisição retorna o mesmo resultado; reutilizar a chave com payload diferente falha.
- [ ] **Liquidação:** executar o mesmo job duas vezes não duplica prêmio; estorno/void também é idempotente.
- [ ] **Odd:** versão obsoleta exige nova confirmação e cada aposta mantém a versão aceita mesmo depois de alterações.
- [ ] **Horário:** servidor rejeita aposta após `locked_at`, independentemente do relógio do cliente.
- [ ] **Evento:** partida, janela de palpites e liquidação têm estados separados; adiamento, suspensão, cancelamento, WO e correção são auditados.
- [ ] **Verificação:** hipótese legal/aviso/retenção estão documentados; arquivo não é público e a deleção de objeto, versões e derivados é comprovável.
- [ ] **Duplicidade:** uma matrícula não verifica duas contas; valor cru não aparece publicamente.
- [ ] **Conflito:** atleta não aposta no próprio time e admin não aposta no evento sob sua responsabilidade.
- [ ] **Chat:** bloqueio, denúncia, mute, rate limit, autorização por canal e sessão revogada foram testados.
- [ ] **Ranking:** regras de temporada, recarga, empate, void e entrada tardia são públicas e determinísticas.
- [ ] **Marca:** telas e domínio não sugerem vínculo oficial; uso de “UFU” e identidade passou por revisão.
- [ ] **Economia:** não existe endpoint ou fluxo escondido para compra, transferência, resgate ou prêmio econômico.
- [ ] **Gate jurídico:** modelo recreativo, política etária, termos, comunicação e marca foram avaliados sem presumir que “crédito virtual” gera isenção automática.

## Estratégias de Recuperação

| Armadilha | Custo | Passos de recuperação |
|-----------|-------|-----------------------|
| Saldo/ledger divergente | ALTO | Congelar apostas, reconciliar eventos, reconstruir projeções do ledger, corrigir por lançamentos compensatórios e publicar incidente |
| Odd histórica sobrescrita | ALTO | Suspender mercado, recuperar auditoria/backups, anular apostas não reproduzíveis e implantar versionamento antes de reabrir |
| Comprovante exposto | ALTO | Revogar acesso, preservar evidência mínima, conter e avaliar risco/dano relevante; comunicar ANPD/titulares no prazo aplicável quando exigido, manter registro do incidente e eliminar cópias acessíveis sem destruir evidência necessária |
| Resultado incorreto antes da liquidação | BAIXO | Corrigir rascunho/provisório com motivo e nova aprovação |
| Resultado incorreto após liquidação | MÉDIO | Suspender mercado, executar estorno idempotente, publicar correção e reliquidar com versão registrada |
| Abuso no chat | MÉDIO | Congelar canal, preservar evidência mínima, moderar conteúdo/contas, ajustar rate limits e reabrir com monitoramento |
| Confusão de vínculo com a UFU | ALTO | Remover ativos/textos, reforçar aviso, revisar nome/identidade e buscar autorização ou rebranding |

## Mapeamento de Armadilhas para Fases

| Armadilha | Fase preventiva | Verificação |
|-----------|-----------------|-------------|
| Deriva para valor real | Fundação/requisitos + gate de lançamento | Invariantes e testes garantem ausência de compra, saque, transferência, conversão, benefício econômico e prêmio; revisão cobre política etária/comunicação |
| Aparência de vínculo UFU | Fundação de marca + lançamento | Revisão de nome, ativos, textos e aviso de independência |
| Vazamento de comprovante | Identidade/verificação | Revisão de hipótese legal/aviso/operadores; teste de autorização, MFA, lifecycle, deleção efetiva e resposta a incidente |
| Duplo gasto/liquidação | Ledger/apostas | Testes concorrentes e de retry, unicidade por operação/revisão e reconciliação zero |
| Odd não reproduzível | Mercados/odds | Alterar/expirar odd, exigir reconfirmação e comprovar que aposta antiga/liquidação não mudam |
| Fechamento/estado incorreto | Eventos/admin | Testes separados de evento/palpite/liquidação para fronteira de tempo, adiamento, suspensão, WO, `void` e retry |
| Conflito de interesse | RBAC/cadastros | Testes temporais de atleta/equipe e admin/evento bloqueados no servidor, com evidência auditável |
| Abuso no chat | Social/moderação | Testes de canal, rate limit, sessão revogada, denúncia, evidência mínima, mute e banimento |
| Ranking injusto | Economia/ranking | Simulação versionada de temporada, populações geral/UFU/privada, entrada tardia, recarga, empate, expiração e `void` |
| Modelo esportivo rígido | Domínio esportivo | Adicionar modalidade de cada família sem alterar o núcleo e executar fixtures de liquidação |

## Fontes

### Primárias e normativas

- Presidência da República — Lei nº 14.790/2023, especialmente definição de aposta/quota fixa (art. 2º) e impedidos de apostar (art. 26): https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2023/lei/l14790.htm
- Ministério da Fazenda — legislação atualizada de apostas de quota fixa; a página listava novas normas de 2026 na data da pesquisa: https://www.gov.br/fazenda/pt-br/composicao/orgaos/secretaria-de-premios-e-apostas/apostas-de-quota-fixa/legislacao
- Presidência da República — texto compilado da LGPD, com princípios de finalidade/necessidade, término, conservação, segurança e incidentes: https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm
- ANPD — Resolução CD/ANPD nº 2/2022; tratamento de pequeno porte não elimina deveres de bases legais, princípios e segurança: https://www.gov.br/anpd/pt-br/acesso-a-informacao/institucional/atos-normativos/regulamentacoes_anpd/resolucao-cd-anpd-no-2-de-27-de-janeiro-de-2022
- ANPD — Perguntas frequentes, incluindo eliminação após o término da finalidade: https://www.gov.br/anpd/pt-br/acesso-a-informacao/perguntas-frequentes
- ANPD — Guia de Segurança da Informação para Agentes de Tratamento de Pequeno Porte: https://www.gov.br/anpd/pt-br/assuntos/noticias/anpd-publica-guia-de-seguranca-para-agentes-de-tratamento-de-pequeno-porte
- ANPD — Regulamento de Comunicação de Incidente: comunicação em três dias úteis quando o incidente puder causar risco ou dano relevante e registro por ao menos cinco anos: https://www.gov.br/anpd/pt-br/canais_atendimento/agente-de-tratamento/comunicado-de-incidente-de-seguranca-cis
- UFU — Resolução e manual que regulamentam a aplicação da Marca UFU: https://comunica.ufu.br/marca-ufu
- Ministério da Justiça e Segurança Pública — Portaria Interministerial MESP/MF/MJSP nº 1/2026, Política Nacional de Prevenção e Enfrentamento à Manipulação de Resultados Esportivos: https://dspace.mj.gov.br/handle/1/16945
- PostgreSQL 18 — isolamento transacional, bloqueios e necessidade de retry em conflitos seriais: https://www.postgresql.org/docs/18/transaction-iso.html
- PostgreSQL 18 — constraints e unicidade: https://www.postgresql.org/docs/18/ddl-constraints.html

### Segurança técnica

- OWASP File Upload Cheat Sheet — autorização, validação, least privilege e limites: https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html
- OWASP WebSocket Security Cheat Sheet — autenticação/autorização por mensagem, validação, rate limit e logging: https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html
- OWASP Logging Cheat Sheet — eventos administrativos, autorização, uploads e lógica suspeita a registrar sem vazar dados: https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html

### Limites de confiança

- A pesquisa não conclui que créditos virtuais estejam automaticamente fora de toda regulação aplicável. A Lei nº 14.790/2023 descreve aposta e quota fixa em termos de valor, prêmio e moeda nacional, mas não foi localizada orientação oficial que enquadre especificamente um bolão universitário gratuito com pontos não conversíveis. A recomendação é preservar rigidamente o escopo sem valor econômico e obter análise jurídica antes do piloto público, monetização ou premiação.
- Usar as palavras “palpite” e “ponto recreativo” melhora a clareza, mas não determina o enquadramento jurídico; prevalecem funcionamento e substância.
- A pesquisa também não conclui que o nome BETUFU ou qualquer uso da sigla esteja autorizado; esse ponto requer revisão específica de marca e, se necessário, diálogo com a universidade.
- As referências de integridade da Lei nº 14.790/2023 e da PNPEMR orientam controles prudenciais; não se afirma que seus deveres regulatórios incidam automaticamente sobre a BETUFU.

---
*Pesquisa de riscos para: BETUFU*
*Pesquisado em: 2026-08-01*
