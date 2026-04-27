package aor.paj.projecto5.bean;

import aor.paj.projecto5.dao.UserDao;
import aor.paj.projecto5.dto.MessageDto;
import aor.paj.projecto5.entity.MessageEntity;
import aor.paj.projecto5.entity.UserEntity;
import aor.paj.projecto5.websocket.ChatEndpoint;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.json.Json;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Stateless
public class MessageBean {
    @Inject
    private UserDao userDao;
    @Inject
    private TokenBean tokenBean;
    @Inject
    private ChatEndpoint chatEndpoint;
    @PersistenceContext(unitName = "project5PU")
    private EntityManager em;

    public void saveAndSendMessage(UserEntity sender, MessageDto messageDto) {
        // 1. Busca cirúrgica do destinatário
        UserEntity receiver = userDao.findUserByUsername(messageDto.getReceiver());
        if (receiver == null) return;

        // 2. Persistência mínima: criamos a Entity apenas com o necessário
        MessageEntity message = new MessageEntity();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(messageDto.getContent());
        message.setSentAt(new Timestamp(System.currentTimeMillis()));

        em.persist(message);

        // Só tentamos enviar se o user estiver online
        // O método getActiveTokenValueByUser no TokenBean deve ser uma query indexada
        if (receiver != null) {
            // Geramos o JSON de forma robusta usando a API do Jakarta
            String wsPayload = Json.createObjectBuilder()
                    .add("type", "CHAT")
                    .add("sender", sender.getUsername())
                    .add("recipient", receiver.getUsername())
                    .add("content", messageDto.getContent())
                    .add("timestamp", message.getSentAt().getTime())
                    .add("read", false)
                    .build()
                    .toString();
            
            chatEndpoint.send(receiver.getId(), wsPayload);
        }
    }

    /**
     * Recupera todas as mensagens enviadas ou recebidas pelo utilizador.
     */
    public List<MessageDto> getHistory(UserEntity user) {
        // Query para buscar mensagens trocadas pelo utilizador
        List<MessageEntity> entities = em.createQuery(
                "SELECT m FROM MessageEntity m WHERE m.sender = :user OR m.receiver = :user ORDER BY m.sentAt ASC", 
                MessageEntity.class)
                .setParameter("user", user)
                .getResultList();

        List<MessageDto> dtos = new ArrayList<>();
        for (MessageEntity m : entities) {
            MessageDto dto = new MessageDto();
            dto.setSender(m.getSender().getUsername());
            dto.setReceiver(m.getReceiver().getUsername());
            dto.setContent(m.getContent());
            dto.setTimestamp(m.getSentAt().getTime());
            dto.setRead(m.isRead());
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Marca todas as mensagens recebidas de um remetente específico como lidas.
     */
    public void markAsRead(UserEntity receiver, String senderUsername) {
        UserEntity sender = userDao.findUserByUsername(senderUsername);
        if (sender == null) return;

        // Atualizamos todas as mensagens não lidas deste remetente para este destinatário
        em.createQuery("UPDATE MessageEntity m SET m.isRead = true WHERE m.receiver = :receiver AND m.sender = :sender AND m.isRead = false")
                .setParameter("receiver", receiver)
                .setParameter("sender", sender)
                .executeUpdate();
    }
}