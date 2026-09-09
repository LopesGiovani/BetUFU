# Seed local BETUFU

O seeder prepara um ambiente descartável para desenvolvimento e UAT. Ele não altera
`infra/keycloak/realm-betufu.json`: as personas humanas são criadas em runtime pela
API Admin do Keycloak e os vínculos operacionais ficam no PostgreSQL.

## Uso

1. Suba a infraestrutura: `node scripts/dev.mjs up daily`.
2. Defina uma chave HMAC local de pelo menos 32 bytes em Base64:
   `BETUFU_MATRICULA_HMAC_KEY_V1`.
3. Execute `node scripts/dev.mjs seed daily` para uma carga rápida ou
   `node scripts/dev.mjs seed full` para importar as 671 partidas do fixture LAUFU.

O comando usa `docker exec` com `psql`, uma transação por execução e UUIDs
determinísticos. Repetir o comando não duplica registros. Se Keycloak ou PostgreSQL
estiverem offline, a execução termina com erro e não usa fallback silencioso.

## Personas

| Persona | E-mail/username | Senha local | Papel | Escopo | URL de teste |
| --- | --- | --- | --- | --- | --- |
| Administrador geral | `admin.geral@betufu.local` | `BetUfuTest@2026` | `ADMINISTRADOR_GERAL` | global | `/operacao` |
| Maker de odds | `maker.odds@betufu.local` | `BetUfuTest@2026` | `ADMINISTRADOR_COMPETICAO` | competição LAUFU 2026 resolvida no banco | `/operacao/odds/mercados/:marketId` |
| Checker de odds | `checker.odds@betufu.local` | `BetUfuTest@2026` | `ADMINISTRADOR_COMPETICAO` | mesma competição do Maker | `/operacao/odds/mercados/:marketId` |
| Moderador | `moderador@betufu.local` | `BetUfuTest@2026` | `MODERADOR` | competição LAUFU 2026 | `/operacao/moderacao` |
| Aluno regular UFU | `aluno@ufu.br` | `BetUfuTest@2026` | nenhum | — | `/arena` |
| Atleta universitário | `atleta@ufu.br` | `BetUfuTest@2026` | nenhum | vínculo esportivo local | `/arena` |
| Conta técnica | `technical@betufu.local` | variável `BETUFU_TECHNICAL_PASSWORD` | client role `TECHNICAL` | realm | uso interno |

As senhas acima são somente para loopback. Não reutilize em homologação ou produção.
O seeder não imprime senha, token, matrícula, HMAC, saldo, aposta ou liquidez.

## Mercados criados

- um mercado `OPEN` para a Arena pública;
- um mercado `OPEN` com versões históricas v1/v2;
- um mercado `SUSPENDED` com override `PROPOSED` para o fluxo maker-checker;
- um mercado `LOCKED` para validar o estado terminal.
