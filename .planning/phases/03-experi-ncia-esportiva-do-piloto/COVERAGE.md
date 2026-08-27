# API Coverage — armazenamento S3 compatível privado

> Cobertura completa por padrão. Opt-outs são decisões explícitas e justificadas.

| capability | decision | reason |
|---|---|---|
| configure-s3-client | INTEGRATE | Conectar o backend ao endpoint S3 compatível por configuração externa, credenciais externas, path-style e cliente HTTP URLConnection. |
| put-object | INTEGRATE | Gravar somente a imagem JPEG/PNG reencodada sob chave opaca no bucket privado. |
| get-object | INTEGRATE | O handler BETUFU lê o objeto processado para entrega pública controlada. |
| head-object | INTEGRATE | Confirmar metadados mínimos e existência sem listar o bucket. |
| delete-object | INTEGRATE | Remover objetos substituídos ou desvinculados após a referência pública ser trocada com segurança. |
| conditional-object-write | INTEGRATE | Evitar troca silenciosa da referência sob concorrência e retry do comando do atleta. |
| list-objects | OPT-OUT | O fluxo do atleta conhece a chave opaca persistida e não deve enumerar o bucket. |
| create-bucket | OPT-OUT | Provisionamento de bucket pertence à infraestrutura; o runtime da aplicação não cria infraestrutura. |
| delete-bucket | OPT-OUT | A aplicação nunca remove o bucket compartilhado. |
| copy-object | OPT-OUT | A substituição grava um novo objeto processado e troca a referência; não copia bytes existentes. |
| multipart-upload | OPT-OUT | Fotos possuem limite pequeno e processamento síncrono aprovado; multipart ampliaria estados e abortos sem necessidade. |
| presigned-upload | OPT-OUT | O browser não fala com o bucket; autenticação, validação e reencodificação permanecem no BFF. |
| presigned-download | OPT-OUT | A entrega passa por handler BETUFU para não expor endpoint, bucket ou object key. |
| object-acl | OPT-OUT | O bucket permanece privado por política; ACL por objeto criaria uma segunda origem de autorização. |
| bucket-policy-management | OPT-OUT | Política do bucket pertence à infraestrutura e não ao domínio esportivo. |
| object-tagging | OPT-OUT | O MVP não usa tags S3 como estado de domínio ou índice. |
| object-versioning-management | OPT-OUT | O histórico autoritativo é a referência versionada no PostgreSQL; habilitar/configurar versionamento do bucket é decisão de infraestrutura. |
| object-lock-retention | OPT-OUT | Fotos opcionais e removíveis não exigem retenção imutável; direitos do titular exigem remoção controlada. |
| server-side-encryption-management | OPT-OUT | O runtime não administra chaves ou políticas do provedor; configuração de criptografia do bucket permanece externa e deverá ser verificada na implantação. |
