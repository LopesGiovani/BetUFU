# Fase 1: Fundação Segura da Plataforma — Histórico da discussão

> **Somente trilha de auditoria.** Este arquivo não deve ser usado como entrada por agentes de pesquisa, planejamento ou execução. As decisões canônicas estão em `01-CONTEXT.md`; este histórico preserva as alternativas consideradas.

**Data:** 2026-08-01
**Fase:** 1 — Fundação Segura da Plataforma
**Áreas discutidas:** Entrega visível da fase, Comportamento da sessão, Ambiente local, Erros da aplicação

---

## Entrega visível da fase

### Prova visível de funcionamento

| Opção | Selecionada |
|---|:---:|
| Estrutura inicial da interface BETUFU com diagnóstico protegido | ✓ |
| Somente estrutura pública mínima | |
| Somente validação técnica | |

**Escolha do usuário:** Estrutura inicial da interface BETUFU em Angular acompanhada de uma tela de diagnóstico protegida.

### Acesso e ambientes do diagnóstico

| Opção | Selecionada |
|---|:---:|
| Conta técnica autenticada em desenvolvimento e homologação | ✓ |
| Qualquer pessoa somente no ambiente local | |
| Administradores também em produção | |

**Escolha do usuário:** Somente conta técnica autenticada em desenvolvimento e homologação; a rota não existe no build de produção.

### Conteúdo do diagnóstico

| Opção | Selecionada |
|---|:---:|
| Estado resumido e seguro | ✓ |
| Somente visão geral | |
| Diagnóstico técnico detalhado | |

**Escolha do usuário:** Mostrar versão/build, ambiente, API, sessão BFF, Keycloak, PostgreSQL, Redis, armazenamento e correlation ID, usando os estados disponível, degradado e indisponível.

### Falha de uma dependência

| Opção | Selecionada |
|---|:---:|
| Abrir em modo degradado | ✓ |
| Bloquear toda a tela | |
| Exibir o último estado conhecido | |

**Escolha do usuário:** Manter o diagnóstico aberto, identificar o componente afetado, permitir nova tentativa e omitir stack trace, segredo e endereço interno.

### Terminologia da interface inicial

| Opção | Selecionada |
|---|:---:|
| Concluir a área usando “estrutura inicial da interface BETUFU” | ✓ |
| Continuar discutindo a estrutura inicial | |

**Escolha do usuário:** Substituir o jargão “Shell Angular” pela expressão clara “estrutura inicial da interface BETUFU”.

---

## Comportamento da sessão

### Prazo por inatividade

| Opção | Selecionada |
|---|:---:|
| Prazos menores para administradores e contas técnicas | |
| Mesmo prazo para todos os usuários | ✓ |
| Outro prazo | |

**Escolha do usuário:** A duração inicialmente considerada de 2 horas foi revista. A decisão final é expirar após 8 horas de inatividade para usuários comuns, administradores e contas técnicas; atividade válida renova o prazo.

### Nova confirmação de MFA

| Opção | Selecionada |
|---|:---:|
| Revalidar MFA após 15 minutos | |
| Revalidar MFA após 30 minutos | |
| Não repetir MFA durante a sessão ativa | ✓ |

**Escolha do usuário:** No MVP, o MFA realizado no login permanece válido até a sessão terminar. A confirmação adicional por operação sensível foi adiada.

### Sessões simultâneas

| Opção | Selecionada |
|---|:---:|
| Permitir sessões simultâneas independentes | ✓ |
| Permitir somente uma sessão por vez | |
| Limitar a quantidade de dispositivos | |

**Escolha do usuário:** Permitir celular, computador e outros dispositivos simultaneamente, cada qual com sua própria expiração por inatividade.

### Logout voluntário

| Opção | Selecionada |
|---|:---:|
| Sair deste dispositivo com opção de sair de todos | |
| Sempre encerrar todas as sessões | |
| Encerrar somente a sessão deste dispositivo | ✓ |

**Escolha do usuário:** “Sair” encerra apenas a sessão atual; o MVP não terá a opção “Sair de todos”.

### Duração máxima absoluta

| Opção | Selecionada |
|---|:---:|
| Definir uma duração máxima absoluta | |
| Usar apenas expiração por inatividade | ✓ |

**Escolha do usuário:** Não haverá duração máxima absoluta no MVP. A sessão poderá continuar enquanto houver atividade válida e expirará após 8 horas sem atividade.

---

## Ambiente local

### Forma de execução

| Opção | Selecionada |
|---|:---:|
| Ambiente híbrido | ✓ |
| Toda a aplicação em contêineres | |

**Escolha do usuário:** Dependências de infraestrutura em contêineres; Spring Boot e Angular executados pelo terminal ou pela IDE.

### Reconciliação com a arquitetura já aprovada

| Opção | Selecionada |
|---|:---:|
| Aprovar dois modos: híbrido diário e perfil completo em contêineres | ✓ |
| Solicitar outra correção para o conflito | |

**Escolha do usuário:** Após a verificação detectar conflito com `OPS-01`, foi aprovado preservar o modo híbrido como padrão diário e acrescentar um perfil completo de validação, com Spring em contêiner, Angular compilado servido por Nginx e serviço local de e-mail. Wrappers, runtimes e imagens permanecem fixados.

### Primeira inicialização

| Opção | Selecionada |
|---|:---:|
| Inicialização automática e reproduzível | ✓ |
| Configuração manual pelos painéis de cada serviço | |

**Escolha do usuário:** Preparar automaticamente banco/migrações, Keycloak, Redis, armazenamento, serviço local de e-mail e uma conta técnica somente local, sem versionar credenciais reais.

### Persistência e reinicialização

| Opção | Selecionada |
|---|:---:|
| Preservar normalmente e oferecer reinicialização explícita | ✓ |
| Recriar os dados em toda inicialização | |

**Escolha do usuário:** Reinícios normais preservam os dados; um comando separado pode apagar apenas os dados locais da BETUFU, após confirmação, e recriar o ambiente.

### Plataformas atendidas

| Opção | Selecionada |
|---|:---:|
| Windows e Linux como plataformas principais; macOS como melhor esforço | ✓ |
| Suporte obrigatório e equivalente aos três sistemas | |

**Escolha do usuário:** Atender Windows 11 com Docker Desktop/WSL2 e Linux no MVP; manter comandos portáveis e compatibilidade com macOS quando possível.

---

## Erros da aplicação

### Apresentação de erros

| Opção | Selecionada |
|---|:---:|
| Mensagem simples, acionável e com código de referência | ✓ |
| Mensagem genérica sem orientação | |
| Detalhes técnicos completos | |

**Escolha do usuário:** Explicar em português o que falhou e o que fazer, fornecer código copiável e nunca revelar detalhes técnicos, endereços internos ou stack traces.

### Erros de formulário

| Opção | Selecionada |
|---|:---:|
| Preservar os dados e indicar erros nos campos | ✓ |
| Limpar o formulário e apresentar erro geral | |

**Escolha do usuário:** Preservar os valores, explicar cada campo inválido, focar o primeiro erro e reservar a mensagem geral para problemas que não pertencem a um campo.

### Novas tentativas

| Opção | Selecionada |
|---|:---:|
| Repetir automaticamente apenas consultas | ✓ |
| Repetir automaticamente qualquer operação | |
| Nunca oferecer nova tentativa | |

**Escolha do usuário:** Consultas podem ser repetidas automaticamente. Ações mutáveis não serão repetidas silenciosamente, terão proteção contra envio duplicado e oferecerão uma nova tentativa segura quando necessário.

### Falhas parciais

| Opção | Selecionada |
|---|:---:|
| Isolar a falha na área afetada | ✓ |
| Bloquear toda a aplicação diante de qualquer erro | |

**Escolha do usuário:** Manter o restante da interface e a navegação disponíveis; usar tela completa somente quando a aplicação não puder iniciar ou continuar com segurança.

---

## Discrição dos agentes posteriores

- O que constitui atividade válida para renovar a sessão.
- Como funcionam regeneração e revogação administrativa ou de segurança, sem confundi-las com o logout voluntário aprovado.
- Nomes exatos dos comandos locais e detalhes de preparação idempotente.
- Quantidade e espera entre novas tentativas automáticas de consultas, códigos de erro e estrutura detalhada de Problem Details.
- Detalhes visuais não definidos da estrutura inicial da interface.

## Ideias adiadas

- Exigir nova confirmação de MFA antes de operações administrativas sensíveis — pós-MVP.
