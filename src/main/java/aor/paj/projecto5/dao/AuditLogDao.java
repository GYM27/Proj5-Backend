package aor.paj.projecto5.dao;

import aor.paj.projecto5.entity.AuditLogEntity;
import aor.paj.projecto5.entity.UserEntity;
import jakarta.ejb.Stateless;
import java.io.Serializable;
import java.util.List;

/**
 * DAO para persistência de Logs de Auditoria.
 */
@Stateless
public class AuditLogDao extends AbstractDao<AuditLogEntity> implements Serializable {

    private static final long serialVersionUID = 1L;

    public AuditLogDao() {
        super(AuditLogEntity.class);
    }

    /**
     * Obtém os logs mais recentes do sistema.
     */
    public List<AuditLogEntity> findLatest(int limit) {
        return em.createQuery("SELECT l FROM AuditLogEntity l ORDER BY l.timestamp DESC", AuditLogEntity.class)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Filtra logs por utilizador.
     */
    public List<AuditLogEntity> findByUser(UserEntity user) {
        return em.createQuery("SELECT l FROM AuditLogEntity l WHERE l.user = :user ORDER BY l.timestamp DESC", AuditLogEntity.class)
                .setParameter("user", user)
                .getResultList();
    }
}
