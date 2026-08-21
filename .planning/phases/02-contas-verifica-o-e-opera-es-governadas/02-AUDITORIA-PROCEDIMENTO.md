# Procedimento de Auditoria

## Objetivo e limite

Este procedimento descreve a trilha `public.audit_event` do MVP fechado da BETUFU. A trilha registra decisões operacionais permitidas e bloqueadas para permitir responsabilização e reconciliação. Ela não substitui logs técnicos nem cria uma ferramenta de investigação irrestrita.

## Dados registrados

Cada evento pode registrar horário, ator e papel no momento da ação, alvo, ação, resultado, referências anterior e nova, motivo, código de correlação e chave de idempotência. O ator pode ser nulo quando a ação pertence ao sistema, como na semente do primeiro administrador.

A trilha deliberadamente não guarda:

- número de matrícula, porque o dado original não deve ser conservado;
- HMAC da matrícula, porque o digest é um pseudônimo sensível e precisa poder ser expurgado após comprometimento da chave;
- senha, token, cookie ou segredo de configuração, porque esses valores permitem autenticação ou reutilização indevida;
- MFA ou evidência documental, porque essas informações não fazem parte do contrato aprovado do MVP.

Quando uma ação envolve matrícula, os valores anterior e novo referenciam a linha de vínculo e a versão da chave, nunca o número ou o digest.

## Acesso e reconciliação

O produto não oferece endpoint de listagem crua da auditoria. A superfície aprovada é somente a visão agregada do painel operacional do Plano 02-10, acessível ao administrador geral. Enquanto esse painel não estiver implementado, não existe leitura de auditoria exposta pelo produto.

Para reconciliar uma operação, o administrador geral deve selecionar no painel o período e a categoria da ação, comparar totais permitidos e bloqueados com a operação esperada e usar o código de correlação para relacionar uma recusa ao relato do usuário. Divergência de quantidade, ação inesperada ou ausência de evento deve ser tratada como incidente e investigada sem copiar dados sensíveis para mensagens, tickets ou logs.

Acesso SQL direto, exportação crua ou concessão temporária de leitura não fazem parte deste procedimento. Qualquer necessidade desse tipo exige decisão, autorização e registro próprios.

## Retenção no MVP fechado

Os eventos são conservados durante toda a vida do ambiente, sem exclusão automática. Isso não é a política definitiva para operação pública. Antes do lançamento público, `PRIV-05` exige revisão jurídica e definição de prazo por classe de dado; o lançamento fica bloqueado enquanto essa decisão estiver aberta.

## Imutabilidade e expurgo futuro

O runtime da aplicação possui somente `SELECT` e `INSERT` sobre `audit_event`. `UPDATE`, `DELETE`, `TRUNCATE` e DDL são negados por privilégio, e um trigger também rejeita alteração ou remoção caso uma permissão seja afrouxada no futuro.

Não existe ferramenta de expurgo da auditoria nesta fase. Uma ferramenta futura precisará contornar deliberadamente o trigger e, por isso, só poderá existir após definição do fundamento, escopo, aprovação, identidade executora, cópia de segurança, validação e registro auditável da própria operação. Ela nunca será uma capacidade do usuário de runtime.

## Comprometimento da chave de matrícula (D-37)

O incidente de chave não remove eventos de `audit_event`. A resposta planejada é:

1. bloquear o uso da versão comprometida para novas escritas e ativar uma nova versão;
2. abrir uma janela para que as pessoas reinformem a matrícula sob a nova chave;
3. identificar na tabela de vínculo `ufu_enrollment` os HMACs da versão comprometida;
4. executar o expurgo desses vínculos somente após ferramenta e autorização específicas;
5. reconciliar quantidades e registrar início, conclusão e desvios do incidente.

A trilha permanece válida porque guarda referências ao vínculo e à versão da chave, não o HMAC. A Fase 2 documenta esse fluxo, mas não implementa a ferramenta de expurgo.
