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
        Long systemNotifications = em.createQuery(
                "SELECT COUNT(n) FROM NotificationEntity n WHERE n.receiver = :user AND n.isRead = false", 
                Long.class)
                .setParameter("user", user)
                .getSingleResult();

        Long unreadMessages = em.createQuery(
                "SELECT COUNT(m) FROM MessageEntity m WHERE m.receiver = :user AND m.isRead = false", 
                Long.class)
                .setParameter("user", user)
                .getSingleResult();

        return (systemNotifications != null ? systemNotifications : 0L) + 
               (unreadMessages != null ? unreadMessages : 0L);
    }

    /**
     * Marca todas as notificações (Sistema e Chat) de um utilizador como lidas.
     */
    public void markAllAsRead(UserEntity user) {
        em.createQuery("UPDATE NotificationEntity n SET n.isRead = true WHERE n.receiver = :user")
          .setParameter("user", user)
          .executeUpdate();

        em.createQuery("UPDATE MessageEntity m SET m.isRead = true WHERE m.receiver = :user")
          .setParameter("user", user)
          .executeUpdate();
    }

    /**
     * Remove permanentemente todas as notificações de sistema de um utilizador.
     */
    public void deleteAllByUser(UserEntity user) {
        em.createQuery("DELETE FROM NotificationEntity n WHERE n.receiver = :user")
          .setParameter("user", user)
          .executeUpdate();
    }

    /**
     * Remove uma única notificação.
     */
    public void deleteByIdAndUser(Long id, UserEntity user) {
        em.createQuery("DELETE FROM NotificationEntity n WHERE n.id = :id AND n.receiver = :user")
          .setParameter("id", id)
          .setParameter("user", user)
          .executeUpdate();
    }
}
