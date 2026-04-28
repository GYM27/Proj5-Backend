package aor.paj.projecto5.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entidade para o Log de Auditoria.
 * Regista todas as atividades críticas do sistema.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true) // null para ações de sistema ou login falhado
    private UserEntity user;

    @Column(nullable = false)
    private String action; // ex: LOGIN, LOGOUT, CREATE, UPDATE, DELETE

    @Column(length = 500)
    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    public AuditLogEntity() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditLogEntity(UserEntity user, String action, String details) {
        this();
        this.user = user;
        this.action = action;
        this.details = details;
    }

    // --- Getters e Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
