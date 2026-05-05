package aor.paj.projecto5.service;

import java.util.List;

import aor.paj.projecto5.bean.ClientsBean;
import aor.paj.projecto5.bean.UserVerificationBean;
import aor.paj.projecto5.dto.ClientsDTO;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * O meu "balcão de atendimento" para tudo o que envolve Clientes.
 * É aqui que defino as rotas (/clients) que o React vai chamar.
 * Repara como uso o 'verifier' em quase todos os métodos para garantir 
 * que ninguém mexe onde não deve.
 */
@Path("/clients")
public class ClientsService {

    @Inject
    ClientsBean clientsBean;

    @Inject
    UserVerificationBean verifier;

    // =========================================================================
    // --- 1. OPERAÇÕES DE UTILIZADOR E DONO ---
    // =========================================================================

    /**
     * Criar um novo cliente.
     * Usei o @Valid para o Java validar automaticamente campos como o email ou nome 
     * antes de sequer tentar chegar à base de dados.
     */
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addClient(@HeaderParam("token") String token, @Valid ClientsDTO clientDto) {
        // SEGURANÇA: Garante que apenas utilizadores com sessão ativa podem criar.
        verifier.verifyUser(token);

        ClientsDTO createdClient = clientsBean.addClient(token, clientDto);

        // BOAS PRÁTICAS REST: Retorna 201 Created quando um novo recurso é gerado.
        return Response.status(201).entity(createdClient).build();
    }

    /**
     * Listagem de clientes. 
     * Fiz isto de forma inteligente: o mesmo endpoint serve para o Admin ver tudo 
     * ou para o vendedor ver só os seus, dependendo do token e do parâmetro userId.
     */
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listClients(
            @HeaderParam("token") String token,
            @QueryParam("userId") Long userId,
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size
    ) {
        verifier.verifyUser(token);
        // O Bean trata a lógica Admin vs User e devolve o DTO paginado
        var response = clientsBean.listClientsPaginated(token, userId, false, search, page, size);
        return Response.ok(response).build();
    }

    /**
     * ATUALIZAÇÃO DE CLIENTE
     */
    @PUT
    @Path("/{id:[0-9]+}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateClient(@PathParam("id") Long clientId,
                                 @HeaderParam("token") String token,
                                 @Valid ClientsDTO clientDto) {
        // SEGURANÇA (RBAC): Valida simultaneamente se o token é válido E se o utilizador
        // tem permissão para editar este recurso específico (é o Dono ou é Admin).
        verifier.verifyOwnershipOrAdmin(token, clientId);

        ClientsDTO updatedClient = clientsBean.editClient(token, clientId, clientDto);
        return Response.ok(updatedClient).build();
    }

    /**
     * Mandar um cliente para a lixeira (Soft Delete).
     * Repara na Regex `:[0-9]+` no Path - isto impede que o Java se confunda 
     * com outras rotas que tenham nomes em vez de IDs.
     */
    @DELETE
    @Path("/{id:[0-9]+}")
    public Response softDelete(@HeaderParam("token") String token, @PathParam("id") Long id) {
        // SEGURANÇA (RBAC): Valida se o utilizador é o dono do registo ou um Administrador
        verifier.verifyOwnershipOrAdmin(token, id);
        clientsBean.softDeleteClient(token, id);

        // BOAS PRÁTICAS REST: 204 No Content indica sucesso numa operação de eliminação
        return Response.noContent().build();
    }

    /**
     * RESTAURO DE CLIENTE
     * -------------------------------------------------------------------------
     * Utiliza PATCH porque estamos a realizar uma atualização parcial (apenas a flag softDelete).
     * Regex `:[0-9]+` aplicada ao PathParam.
     */
    @PATCH
    @Path("/{id:[0-9]+}/restore")
    @Produces(MediaType.APPLICATION_JSON) // Garante que o cabeçalho HTTP devolve o formato correto
    public Response restoreClient(@HeaderParam("token") String token, @PathParam("id") Long id) {
        // SEGURANÇA (RBAC): Verificação de posse ou privilégios globais
        verifier.verifyOwnershipOrAdmin(token, id);

        // Guarda o DTO retornado pelo Bean após a alteração de estado
        ClientsDTO restoredClient = clientsBean.restoreClient(token, id);

        // Retorna o objeto completo para que o Frontend possa atualizar a UI sem fazer novos pedidos
        return Response.ok(restoredClient).build();
    }

    /**
     * Endpoint: Lixeira exclusiva do próprio utilizador (User Normal)
     * Rota React: api("/clients/me-trash", "GET")
     */
    @GET
    @Path("/me-trash")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMyTrash(
            @HeaderParam("token") String token,
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        verifier.verifyUser(token);
        // Lixeia paginada para o próprio utilizador
        var response = clientsBean.listClientsPaginated(token, null, true, search, page, size);
        return Response.ok(response).build();
    }


    // =========================================================================
    // --- 2. OPERAÇÕES EXCLUSIVAS DE ADMINISTRADOR ---
    // =========================================================================

    /**
     * Ação irreversível! 
     * Apenas o Admin pode fazer isto para limpar mesmo o cliente da memória do PostgreSQL.
     */
    @DELETE
    @Path("/{id:[0-9]+}/permanent")
    public Response permanentDelete(@HeaderParam("token") String token, @PathParam("id") Long id) {
        // 1. SEGURANÇA ESTRITA: Apenas o perfil Administrador tem acesso a eliminações físicas
        verifier.verifyAdmin(token);

        // 2. Chama o método de execução destrutiva no Bean
        clientsBean.permanentDeleteClient(token, id);

        // 3. Retorna 204 No Content
        return Response.noContent().build();
    }

    /**
     * AÇÃO EM LOTE: Desativar todos os clientes de um utilizador.
     */
    @PATCH
    @Path("/user/{userId}/status/deactivate-all")
    public Response softDeleteAllUserClients(
            @HeaderParam("token") String token,
            @PathParam("userId") Long userId) {
        verifier.verifyAdmin(token);
        // LÓGICA DE PERFORMANCE: Bulk Update em vez de iterar cliente a cliente.
        clientsBean.softDeleteAllClientsByUser(userId);
        return Response.ok().build();
    }

    /**
     * LIXEIRA POR UTILIZADOR (Visão de Admin)
     */
    @GET
    @Path("/user/{userId}/trash")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDeletedClientsByUserId(
            @HeaderParam("token") String token,
            @PathParam("userId") Long userId,
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        verifier.verifyAdmin(token);
        var response = clientsBean.listClientsPaginated(token, userId, true, search, page, size);
        return Response.ok(response).build();
    }

    /**
     * AÇÃO EM LOTE: Restaurar todos os clientes de um utilizador.
     */
    @PATCH
    @Path("/user/{userId}/status/activate-all")
    public Response restoreAllUserClients(
            @HeaderParam("token") String token,
            @PathParam("userId") Long userId) {
        verifier.verifyAdmin(token);
        clientsBean.unSoftDeleteAllClientsByUser(userId); // Agora usa Bulk Update
        return Response.ok().build();
    }

    /**
     * AÇÃO DE LIMPEZA GERAL (Hard Delete em Lote)
     */
    @DELETE
    @Path("/user/{userId}/trash")
    public Response emptyTrash(
            @PathParam("userId") Long userId,
            @HeaderParam("token") String token) {
        verifier.verifyAdmin(token);
        clientsBean.emptyTrash(userId);
        return Response.noContent().build();
    }

    /**
     * CRIAÇÃO DE CLIENTE POR PROCURAÇÃO
     * O Admin cria o cliente, mas atribui-o a outro utilizador.
     */
    @POST
    @Path("/user/{userId}")
    public Response createClientForUser(
            @PathParam("userId") Long userId,
            @Valid ClientsDTO dto,
            @HeaderParam("token") String token) {
        verifier.verifyAdmin(token);
        ClientsDTO created = clientsBean.createClientForUser(userId, dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /**
     * LIXEIRA GLOBAL (Visão de Admin de todos os apagados)
     */
    @GET
    @Path("/trash")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllDeletedClients(
            @HeaderParam("token") String token,
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        verifier.verifyAdmin(token);
        var response = clientsBean.listClientsPaginated(token, null, true, search, page, size);
        return Response.ok(response).build();
    }
}