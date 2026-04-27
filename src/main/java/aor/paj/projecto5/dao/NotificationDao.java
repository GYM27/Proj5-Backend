package aor.paj.projecto5.dao;

import aor.paj.projecto5.entity.NotificationEntity;
import aor.paj.projecto5.entity.UserEntity;
import jakarta.ejb.Stateless;
import java.io.Serializable;
import java.util.List;

@Stateless
public class NotificationDao extends AbstractDao<NotificationEntity> implements Serializable {

    public NotificationDao() {
        super(NotificationEntity.class);
    }

    /**
     * Procura todas as notificações de um utilizador, ordenadas pela mais recente.
     */
    public List<NotificationEntity> findByUser(UserEntity user) {
        return em.createQuery(
                "SELECT n FROM NotificationEntity n WHERE n.receiver = :user ORDER BY n.creatAt DESC", 
                NotificationEntity.class)
                .setParameter("user", user)
                .getResultList();
    }

    /**
     * Conta as notificações não lidas de um utilizador.
     */
    public Long countUnread(UserEntity user) {
        return em.createQuery(
                "SELECT COUNT(n) FROM NotificationEntity n WHERE n.receiver = :user AND n.isRead = false", 
                Long.class)
                .setParameter("user", user)
                .getSingleResult();
    }

    /**
     * Marca todas as notificações de um utilizador como lidas.
     */
    public void markAllAsRead(UserEntity user) {
        em.createQuery("UPDATE NotificationEntity n SET n.isRead = true WHERE n.receiver = :user")
          .setParameter("user", user)
          .executeUpdate();
    }
}
