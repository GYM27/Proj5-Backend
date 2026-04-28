package aor.paj.projecto5.service;

import aor.paj.projecto5.bean.MessageBean;
import aor.paj.projecto5.bean.NotificationBean;
import aor.paj.projecto5.bean.UserVerificationBean;
import aor.paj.projecto5.dao.NotificationDao;
import aor.paj.projecto5.dto.NotificationDTO;
import aor.paj.projecto5.entity.MessageEntity;
import aor.paj.projecto5.entity.NotificationEntity;
import aor.paj.projecto5.entity.UserEntity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * O meu serviço de alertas unificado. 
 */
@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationService {

    @Inject
    private NotificationDao notificationDao;

    @Inject
    private NotificationBean notificationBean;

    @Inject
    private MessageBean messageBean;

    @Inject
    private UserVerificationBean verifier;

    /**
     * GET /notifications -> Retorna a lista unificada.
     */
    @GET
    public Response getMyNotifications(@HeaderParam("token") String token) {
        UserEntity user = verifier.verifyUser(token);
        
        List<NotificationDTO> dtos = new ArrayList<>();

        List<NotificationEntity> sysNotifs = notificationDao.findByUser(user);
        for (NotificationEntity e : sysNotifs) {
            NotificationDTO dto = new NotificationDTO();
            dto.setId(e.getId());
            dto.setContent(e.getContent());
            dto.setType(e.getType().name());
            dto.setRead(e.isRead());
            dto.setCreatAt(e.getCreatAt());
            dtos.add(dto);
        }

        List<MessageEntity> unreadMessages = messageBean.getUnreadMessages(user);
        for (MessageEntity m : unreadMessages) {
            NotificationDTO dto = new NotificationDTO();
            dto.setId(m.getId() + 1000000); 
            dto.setContent("Nova mensagem de " + m.getSender().getFirstName() + ": " + m.getContent());
            dto.setType("CHAT");
            dto.setRead(false);
            dto.setCreatAt(m.getSentAt());
            dtos.add(dto);
        }

        Collections.sort(dtos, (d1, d2) -> d2.getCreatAt().compareTo(d1.getCreatAt()));
        
        return Response.ok(dtos).build();
    }

    @PATCH
    @Path("/{id}/read")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response markAsRead(@HeaderParam("token") String token, @PathParam("id") Long id) {
        verifier.verifyUser(token);
        if (id < 1000000) {
            notificationBean.markAsRead(id);
        }
        return Response.ok().build();
    }

    @POST
    @Path("/read-all")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response markAllAsRead(@HeaderParam("token") String token) {
        UserEntity user = verifier.verifyUser(token);
        notificationDao.markAllAsRead(user);
        return Response.ok().build();
    }

    /**
     * DELETE /notifications -> Limpa o histórico.
     */
    @DELETE
    public Response deleteAll(@HeaderParam("token") String token) {
        UserEntity user = verifier.verifyUser(token);
        notificationDao.deleteAllByUser(user);
        return Response.ok("{\"message\":\"Histórico de notificações limpo.\"}").build();
    }

    /**
     * DELETE /notifications/{id} -> Limpa uma notificação.
     */
    @DELETE
    @Path("/{id}")
    public Response deleteOne(@HeaderParam("token") String token, @PathParam("id") Long id) {
        UserEntity user = verifier.verifyUser(token);
        if (id < 1000000) {
            notificationDao.deleteByIdAndUser(id, user);
        }
        return Response.ok().build();
    }

    @GET
    @Path("/unread-count")
    public Response getUnreadCount(@HeaderParam("token") String token) {
        UserEntity user = verifier.verifyUser(token);
        Long count = notificationDao.countUnread(user);
        return Response.ok(count).build();
    }
}
