package aor.paj.projecto5.service;

import aor.paj.projecto5.bean.NotificationBean;
import aor.paj.projecto5.bean.UserVerificationBean;
import aor.paj.projecto5.dao.NotificationDao;
import aor.paj.projecto5.dto.NotificationDTO;
import aor.paj.projecto5.entity.NotificationEntity;
import aor.paj.projecto5.entity.UserEntity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * O meu serviço de alertas. 
 * É aqui que o Frontend vem buscar a lista de notificações (sininho no Header).
 */
@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotificationService {

    @Inject
    private NotificationDao notificationDao;

    @Inject
    private NotificationBean notificationBean;

    @Inject
    private UserVerificationBean verifier;

    /**
     * Vai buscar as notificações de quem está logado. 
     * Fiz a conversão para DTO aqui mesmo no loop para ser mais direto.
     */
    @GET
    public Response getMyNotifications(@HeaderParam("token") String token) {
        UserEntity user = verifier.verifyUser(token);
        List<NotificationEntity> entities = notificationDao.findByUser(user);
        
        List<NotificationDTO> dtos = new ArrayList<>();
        for (NotificationEntity e : entities) {
            NotificationDTO dto = new NotificationDTO();
            dto.setId(e.getId());
            dto.setContent(e.getContent());
            dto.setType(e.getType().name());
            dto.setRead(e.isRead());
            dto.setCreatAt(e.getCreatAt());
            dtos.add(dto);
        }
        
        return Response.ok(dtos).build();
    }

    /**
     * Quando o utilizador clica numa notificação, aviso o Bean para mudar o estado para "lida".
     */
    @PATCH
    @Path("/{id}/read")
    public Response markAsRead(@HeaderParam("token") String token, @PathParam("id") Long id) {
        verifier.verifyUser(token);
        notificationBean.markAsRead(id);
        return Response.ok().build();
    }

    /**
     * Marca TODAS as notificações do utilizador como lidas.
     */
    @POST
    @Path("/read-all")
    public Response markAllAsRead(@HeaderParam("token") String token) {
        UserEntity user = verifier.verifyUser(token);
        notificationDao.markAllAsRead(user);
        return Response.ok().build();
    }
}
