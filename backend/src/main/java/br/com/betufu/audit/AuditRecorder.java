package br.com.betufu.audit;

public interface AuditRecorder {

    /**
     * Records an audit event into the append-only audit trail.
     *
     * @param entry the audit entry containing event metadata
     */
    void record(AuditEntry entry);
}
