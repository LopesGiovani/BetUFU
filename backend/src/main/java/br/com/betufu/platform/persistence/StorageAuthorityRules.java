package br.com.betufu.platform.persistence;

/**
 * Declaração formal das regras de autoridade e delimitação de armazenamento (DATA-01, DATA-06).
 *
 * 1. PostgreSQL 18 é a única fonte autoritativa de persistência para saldo, identidade, apostas e regras de negócio.
 * 2. Redis é estritamente delimitado como armazenamento auxiliar efêmero (sessão HTTP com TTL e caches voláteis).
 * 3. S3 (SeaweedFS local) é estritamente delimitado para objetos privados de mídia/comprovantes.
 * 4. O módulo platform não acessa pacotes internos de outros módulos (ex: identity.internal).
 */
public final class StorageAuthorityRules {

    public static final String AUTHORITATIVE_DATABASE_NAME = "PostgreSQL 18";
    public static final String AUXILIARY_CACHE_NAME = "Redis";
    public static final String OBJECT_STORAGE_NAME = "S3";

    private StorageAuthorityRules() {}
}
