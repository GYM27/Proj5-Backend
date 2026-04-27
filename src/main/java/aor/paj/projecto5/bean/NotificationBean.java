package aor.paj.projecto5.bean;

import aor.paj.projecto5.dao.NotificationDao;
import aor.paj.projecto5.entity.NotificationEntity;
import aor.paj.projecto5.entity.UserEntity;
import aor.paj.projecto5.utils.NotificationType;
import aor.paj.projecto5.websocket.ChatEndpoint;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.io.Serializable;

/**
 * O motor dos meus alertas. 
 * Decidi centralizar aqui a criação de notificações porque elas precisam de fazer 
 * duas coisas: gravar-se na Base de Dados e avisar o utilizador via WebSocket.
 */
@Stateless
public class NotificationBean implements Serializable {

    @Inject
    private NotificationDao notificationDao;

    @Inject
    private ChatEndpoint chatEndpoint;

    /**
     * Onde a "magia" do Real-Time acontece. 
     * Além de guardar a notificação na BD (para quando o utilizador fizer refresh), 
     * eu tento disparar uma mensagem pelo ChatEndpoint. Se ele estiver ligado, 
     * o alerta aparece-lhe no ecrã sem ele fazer nada!
     */
    public void createNotification(UserEntity receiver, NotificationType type, String content) {
        NotificationEntity notification = new NotificationEntity();
        notification.setReceiver(receiver);
        notification.setType(type);
        notification.setContent(content);
        notification.setRead(false);

        notificationDao.persist(notification);

        // Prepara o JSON para o WebSocket (Formato que o Frontend já espera)
        String wsMessage = String.format(
            "{\"type\": \"NOTIFICATION\", \"content\": \"%s\", \"id\": %d}",
            content, notification.getId()
        );

        // Envia em tempo real se o utilizador estiver online
        chatEndpoint.send(receiver.getId(), wsMessage);
    }

    /**
     * Marca uma notificação como lida.
     */
    public void markAsRead(Long notificationId) {
        NotificationEntity n = notificationDao.find(notificationId);
        if (n != null) {
            n.setRead(true);
            notificationDao.merge(n);
        }
    }
}
