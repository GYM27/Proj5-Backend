package aor.paj.projecto5.service;

import java.util.List;

import aor.paj.projecto5.bean.LeadsBean;
import aor.paj.projecto5.bean.LoginBean;
import aor.paj.projecto5.bean.UserVerificationBean;
import aor.paj.projecto5.dto.LeadDTO;
import aor.paj.projecto5.entity.UserEntity;
import aor.paj.projecto5.exception.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("leads")
public class LeadService {

    @Inject
    LeadsBean leadsBean;
    @Inject
    LoginBean loginBean;
    @Inject
    UserVerificationBean verifier;


    /**
     * Initializes the creation of a new lead.
     * Validates input data integrity according to Bean Validation constraints.
     */
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createLead(@HeaderParam("token") String token,
                               @Valid LeadDTO leadDTO) {

        // 1. Validação de token e estado do utilizador
        verifier.verifyUser(token);
        // 2. Persistência da entidade através do bean de negócio
        LeadDTO created = leadsBean.addLead(token, leadDTO);
        // 3. Retorno do recurso criado com status 201
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /**
     * Recupera a lista de leads do utilizador autenticado com suporte a filtros e paginação.
     */
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLeads(
            @HeaderParam("token") String token,
            @QueryParam("softDeleted") Boolean softDeleted,
            @QueryParam("state") Integer stateId,
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size
    ) {
        UserEntity user = verifier.verifyUser(token);
        
        // Paginação e filtragem no Backend
        var response = leadsBean.getLeadsPaginated(stateId, user.getId(), softDeleted, search, page, size);
        return Response.ok(response).build();
    }

    /**
     * Recupera os detalhes de uma lead específica através do seu identificador.
     * Verifica a propriedade do recurso antes de permitir o acesso.
     */
    @GET
    @Path("/{leadId:[0-9]+}") // Retiramos o /me para padronizar com /clients
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLeadById(@HeaderParam("token") String token,
                                @PathParam("leadId") Long leadId) {

        // Validação de acesso e existência do recurso
        verifier.verifyLeadOwnership(token, leadId);

        // 2. LÓGICA: Se o código chegou aqui, é porque a lead existe e o user tem permissão.
        // O Bean agora só precisa de ir buscar e converter.
        LeadDTO lead = leadsBean.getLeadById(leadId);

        return Response.ok(lead).build();
    }

    @PUT
    @Path("/{leadId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLead(@HeaderParam("token") String token,
                               @PathParam("leadId") Long leadId,
                               @Valid LeadDTO leadDTO) {

        // 1. O "polícia" faz todas as verificações de token e posse
        verifier.verifyLeadOwnership(token, leadId);

        // 2. O Bean executa apenas a atualização dos dados
        LeadDTO updated = leadsBean.editLead(leadId, leadDTO);

        return Response.ok(updated).build();
    }


    /**
     * Realiza a eliminação lógica (soft delete) de uma lead.
     */
    @DELETE
    @Path("/{leadId}") // URL limpa e profissional
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteLead(@HeaderParam("token") String token,
                               @PathParam("leadId") Long leadId) {

        // A segurança continua a ser a mesma
        verifier.verifyLeadOwnership(token, leadId);

        // O Bean faz o mesmo trabalho (setSoftDelete(true))
        leadsBean.softDeleteLead(leadId);

        return Response.ok(new ErrorResponse("Lead eliminada com sucesso", 200)).build();
    }




    //*************************Secção do Administrador***************************************************************

    /**
     * Restaura uma lead do estado de eliminação lógica.
     */
    @PATCH
    @Path("/{leadId}/restore")
    @Produces(MediaType.APPLICATION_JSON)
    public Response restoreLead(@HeaderParam("token") String token,
                                @PathParam("leadId") Long leadId) {

        // 1. O verifier garante que a lead pertence ao utilizador
        verifier.verifyLeadOwnership(token, leadId);

        // 2. O Bean faz o trabalho de mudar a flag na BD
        leadsBean.restoreLead(leadId);

        return Response.ok(new ErrorResponse("Lead restaurada com sucesso", 200)).build();
    }

    /**
     * Endpoint administrativo para listagem global de leads com filtros avançados.
     */
    @GET
    @Path("/admin")
    @Produces(MediaType.APPLICATION_JSON)
    public Response adminGetLeads(
            @HeaderParam("token") String token,
            @QueryParam("state") Integer stateId,
            @QueryParam("userId") Long userId,
            @QueryParam("softDeleted") Boolean softDeleted,
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size
    ) {
        verifier.verifyAdmin(token);
        var response = leadsBean.getLeadsPaginated(stateId, userId, softDeleted, search, page, size);
        return Response.ok(response).build();
    }


    /**
     * Permite a edição administrativa de qualquer lead no sistema.
     */
    @PUT
    @Path("/admin/{leadId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response superAdminUpdate(
            @HeaderParam("token") String token,
            @PathParam("leadId") Long leadId,
            LeadDTO dto // O DTO traz os novos valores (título, estado, ownerId, softDelete)
    ) {
        // 1. Segurança: Apenas Admins ativos
        verifier.verifyAdmin(token);

        // 2. Execução: O Bean atualiza a entidade com base no que o DTO trouxer
        LeadDTO updated = leadsBean.adminSuperEdit(leadId, dto);

        return Response.ok(updated).build();
    }


    /**
     * Admin: Cria uma nova lead e atribui-a a um utilizador específico.
     */
    @POST
    @Path("/admin/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response adminAddLeadToUser(@HeaderParam("token") String token,
                                       @PathParam("userId") Long userId,
                                       @Valid LeadDTO leadDTO) {

        // 1. Segurança: Verifica se quem chama é um ADMIN ativo
        verifier.verifyAdmin(token);

        // 2. Execução: O Bean cria a lead para o utilizador de destino
        LeadDTO created = leadsBean.addLeadToUser(userId, leadDTO);

        // 3. Resposta: 201 Created com o objeto completo
        return Response.status(Response.Status.CREATED).entity(created).build();
    }


    /**
     * Eliminação definitiva (hard delete) de uma lead do sistema.
     */
    @DELETE
    @Path("/admin/{leadId}")
    public Response hardDelete(@HeaderParam("token") String token,
                               @PathParam("leadId") Long leadId) {

        // 1. Segurança: Só admins entram
        verifier.verifyAdmin(token);
        // 2. Execução: Remove a linha da tabela
        leadsBean.hardDeleteLead(leadId);
        // 3. Resposta: 204 No Content (sucesso para remoções definitivas)
        return Response.noContent().build();
    }

    /**
     * Operação em lote para marcação de todas as leads de um utilizador para eliminação lógica.
     */
    @POST
    @Path("/admin/{userId}/softdeleteall")
    @Produces(MediaType.APPLICATION_JSON)
    public Response softDeleteAllFromUser(@HeaderParam("token") String token,
                                          @PathParam("userId") Long userId) {

        // 1. Segurança: Apenas administradores
        verifier.verifyAdmin(token);

        // 2. Execução: O Bean faz o update em massa
        int totalAlterado = leadsBean.softDeleteAllFromUser(userId);

        // 3. Resposta: Informamos o React de quantas leads foram "limpas"
        return Response.ok(new ErrorResponse(totalAlterado + " leads movidas para a lixeira.", 200)).build();
    }

    /**
     * Ações em lote: Recupera todas as leads de um utilizador que estavam na lixeira.
     * Útil para reverter um erro ou quando um utilizador volta a ficar ativo.
     */
    @POST
    @Path("/admin/{userId}/softundeleteall")
    @Produces(MediaType.APPLICATION_JSON)
    public Response undeleteAllFromUser(@HeaderParam("token") String token,
                                        @PathParam("userId") Long userId) {

        // 1. Segurança: Verificação de Administrador
        verifier.verifyAdmin(token);

        // 2. Execução: O Bean executa o Update em massa na BD
        int totalRecuperado = leadsBean.undeleteAllFromUser(userId);

        // 3. Resposta: Informamos o Admin do impacto da ação
        return Response.ok(new ErrorResponse(totalRecuperado + " leads recuperadas com sucesso.", 200)).build();
    }

    @DELETE
    @Path("/admin/{userId}/trash")
    @Produces(MediaType.APPLICATION_JSON)
    public Response emptyTrashByUserId(
            @HeaderParam("token") String token,
            @PathParam("userId") Long userId) {


        // 1. Segurança: Verificação estrita de Administrador
        verifier.verifyAdmin(token);

        // 2. Execução: O Bean executa o Delete definitivo (Hard Delete) na BD
        // (Certifica-te que o teu leadsBean tem um método chamado emptyTrash ou similar que devolva o int de linhas apagadas)
        int totalApagado = leadsBean.emptyTrash(userId);

        // 3. Resposta: Seguindo o teu padrão, informamos o Admin do impacto da ação
        return Response.ok(new ErrorResponse(totalApagado + " leads eliminadas permanentemente com sucesso.", 200)).build();
    }

}
