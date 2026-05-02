package aor.paj.projecto5.service;

import aor.paj.projecto5.bean.DashboardBean;
import aor.paj.projecto5.bean.UserVerificationBean;
import aor.paj.projecto5.dto.DashboardStatsDTO;
import aor.paj.projecto5.entity.UserEntity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * O serviço que alimenta os gráficos e contadores da minha página principal.
 */
@Path("/dashboard")
public class DashboardService {

    @Inject
    DashboardBean dashboardBean;

    @Inject
    UserVerificationBean verifier;

    /**
     * Pede as estatísticas globais (Admin) ou pessoais (Vendedor).
     * O 'dashboardBean' decide o que somar dependendo do cargo de quem pede.
     */
    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDashboardStats(@HeaderParam("token") String token, @QueryParam("userId") Long targetUserId) {
        // Valida token e obtém utilizador associado (Entity)
        UserEntity user = verifier.verifyUser(token);
        
        DashboardStatsDTO stats = dashboardBean.getStats(user.getId(), user.getUserRole().name(), targetUserId);
        return Response.ok(stats).build();
    }
}
