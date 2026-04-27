package aor.paj.projecto5.service;

import aor.paj.projecto5.bean.UserVerificationBean;
import aor.paj.projecto5.dto.EmailDTO;
import aor.paj.projecto5.dto.LoginDTO;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import aor.paj.projecto5.bean.UsersBean;
import aor.paj.projecto5.dto.UserBaseDTO;
import aor.paj.projecto5.dto.UserDTO;
import aor.paj.projecto5.dto.UserUpdateDTO;
import aor.paj.projecto5.entity.UserEntity;
import aor.paj.projecto5.utils.UserRoles;
import java.util.List;

/**
 * A minha porta de entrada principal (Endpoint REST) para tudo o que tem a ver com Utilizadores.
 * É aqui que o Frontend (React) bate à porta. Como vês, uso anotações do JAX-RS (@Path, @GET, @POST)
 * para rotear os pedidos para os Beans corretos.
 */
@Path("/users")
public class UserService {

    @Inject
    UsersBean usersBean;

    @Inject
    UserVerificationBean verifier;

    // =========================================================================
    // SEÇÃO DE UTILIZADOR COMUM (Perfil Próprio e Registo)
    // =========================================================================

    /**
     * PASSO 1 (ADMIN): Enviar Convites.
     * Criei este endpoint fechado a sete chaves: só os Administradores podem entrar aqui 
     * (garantido pelo verifier.verifyAdmin). Ele recebe um email e dispara o fluxo de pré-registo.
     * URL: POST /users/invite
     */
    @POST
    @Path("/invite")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response inviteUser(@Valid EmailDTO emailDTO, @HeaderParam("token") String adminToken) {

        // 1. Garante que quem está a tentar enviar o convite é um Administrador
        verifier.verifyAdmin(adminToken);

        // 2. Chama o Passo 1 do nosso Bean (que vai validar se já existe e gerar o Token)
        usersBean.requestRegistration(emailDTO.getEmail());

        return Response.ok("{\"message\":\"Convite enviado com sucesso para " + emailDTO.getEmail() + "\"}").build();
    }

    /**
     * PASSO 2: Concluir Registo.
     * Este endpoint é público porque a pessoa que recebeu o email ainda não tem conta.
     * O segredo está no token que vem no URL (?token=...). Sem ele, não entra.
     * URL: POST /users/register?token=123-abc-456
     */
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response completeRegistration(@QueryParam("token") String token, @Valid UserDTO userDTO) {

        // 1. Valida se o token foi enviado no link
        if (token == null || token.isEmpty()) {
            return Response.status(400).entity("{\"error\":\"Token de convite em falta.\"}").build();
        }

        // 2. Chama o Passo 2 do nosso Bean (valida token, valida email, cria utilizador ACTIVE)
        usersBean.completeRegistration(token, userDTO);

        return Response.status(Response.Status.CREATED)
                .entity("{\"message\":\"Conta criada com sucesso! Já pode fazer login.\"}")
                .build();
    }

    /**
     * Devolve os dados da pessoa que está logada.
     * O frontend nem precisa de enviar o ID, basta mandar o token no Header e eu descubro quem é.
     * URL: GET /users/me
     */
    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMyProfile(@HeaderParam("token") String token) {
        verifier.verifyUser(token);
        UserDTO user = usersBean.getUserDTOByToken(token);
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(user).build();
    }

    /**
     * Grava as edições que o utilizador fez ao seu próprio perfil.
     * Criei o UserUpdateDTO para garantir que ele não tenta enviar um 'role' ou 'state' falso para se promover a Admin.
     * URL: PUT /users/me
     */
    @PUT
    @Path("/me")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response putEditMyProfile(@HeaderParam("token") String token, @Valid UserUpdateDTO userDTO) {
        verifier.verifyUser(token);
        usersBean.putEditOwnUser(token, userDTO);
        
        return Response.ok("{\"message\":\"Perfil atualizado com sucesso\"}").build();
    }


    // =========================================================================
    // SEÇÃO DE ADMINISTRADOR (Gestão de Terceiros)
    // =========================================================================

    /**
     * Lista todos os utilizadores (apenas Admin).
     * URL: GET /users
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllUsers(@HeaderParam("token") String token) {
        // 1. Validação básica: Utilizador tem de estar autenticado e ATIVO
        UserEntity requester = verifier.verifyUser(token);

        List<UserBaseDTO> users;
        
        // 2. Lógica de visibilidade:
        // Se for Admin, vê tudo (para gestão). 
        // Se for utilizador comum, vê apenas os colegas ativos (para o chat).
        if (requester.getUserRole() == UserRoles.ADMIN) {
            users = usersBean.getAllUsers();
        } else {
            users = usersBean.getAllActiveUsers();
        }
        
        return Response.ok(users).build();
    }

    /**
     * Obtém um utilizador específico por ID (apenas Admin).
     * URL: GET /users/{id}
     */
    @GET
    @Path("/{id:[0-9]+}") // REGEX ADICIONADA
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserById(@PathParam("id") Long id, @HeaderParam("token") String token) {
        verifier.verifyAdmin(token);
        UserBaseDTO u = usersBean.getUserBaseDTOById(id);
        if (u == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(u).build();
    }

//    /**
//     * Edita qualquer utilizador por ID (apenas Admin).
//     * URL: PUT /users/{id}
//     */
//    @PUT
//    @Path("/{id:[0-9]+}") // REGEX ADICIONADA
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response adminEditUser(
//            @PathParam("id") Long id,
//            @HeaderParam("token") String token,
//            @Valid UserBaseDTO userBaseDTO) {
//
//        verifier.verifyAdmin(token);
//        usersBean.putEditUser(id, userBaseDTO);
//        return Response.ok(userBaseDTO).build();
//    }

    /**
     * Desativa um utilizador (Soft Delete).
     * URL: PATCH /users/{id}/deactivate
     */
    @PATCH
    @Path("/{id:[0-9]+}/deactivate") // REGEX ADICIONADA
    @Produces(MediaType.APPLICATION_JSON)
    public Response deactivateUser(@PathParam("id") Long id, @HeaderParam("token") String token) {
        verifier.verifyAdmin(token);
        usersBean.softDeleteUser(id);
        return Response.ok("{\"message\":\"Utilizador desativado.\"}").build();
    }

    /**
     * Reativa um utilizador.
     * URL: PATCH /users/{id}/activate
     */
    @PATCH
    @Path("/{id:[0-9]+}/activate") // REGEX ADICIONADA
    @Produces(MediaType.APPLICATION_JSON)
    public Response activateUser(@PathParam("id") Long id, @HeaderParam("token") String token) {
        verifier.verifyAdmin(token);
        usersBean.softUnDeleteUser(id);
        return Response.ok("{\"message\":\"Utilizador reativado.\"}").build();
    }

    /**
     * Remove permanentemente e reatribui leads ao user 999.
     * URL: DELETE /users/{id}
     */
    @DELETE
    @Path("/{id:[0-9]+}") // REGEX ADICIONADA
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteUser(@PathParam("id") Long id, @HeaderParam("token") String token) {
        verifier.verifyAdmin(token);
        usersBean.deleteUser(id);
        return Response.ok("{\"message\":\"Utilizador removido permanentemente.\"}").build();
    }

    @GET
    @Path("/username/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserByUsername(@PathParam("username") String username) {
        UserBaseDTO user = usersBean.getUserBaseDTOByUsername(username);
        return Response.ok(user).build();
    }


    /**
     * O utilizador clica em "Recuperar Password" e cai aqui.
     * Por questões de segurança, eu devolvo SEMPRE "Ok" (mesmo que o email não exista).
     * Assim previno ataques de enumeração (hackers não conseguem saber quem está registado).
     * URL: POST /users/forgot-password
     */
    @POST
    @Path("/forgot-password")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response forgotPassword(@Valid EmailDTO emailDTO) {
        usersBean.requestPasswordReset(emailDTO.getEmail());
        // Retornamos sempre sucesso por segurança (Privacy)
        return Response.ok("{\"message\":\"Se o email existir, receberá um link de recuperação.\"}").build();
    }

    /**
     * Endpoint para definir a nova password.
     * URL: POST /users/reset-password?token=...
     */
    @POST
    @Path("/reset-password")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response resetPassword(@QueryParam("token") String token, LoginDTO loginDTO) {
        // Usamos o LoginDTO porque já tem o campo password, ou podes criar um NewPasswordDTO
        if (token == null || loginDTO.getPassword() == null) {
            throw new WebApplicationException("Token ou Password em falta.", 400);
        }

        usersBean.resetPassword(token, loginDTO.getPassword());
        return Response.ok("{\"message\":\"Password atualizada com sucesso.\"}").build();
    }

}