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
 * Endpoint REST para Utilizadores.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserService {

    @Inject
    UsersBean usersBean;

    @Inject
    UserVerificationBean verifier;

    /**
     * POST /users/invite -> Envia convite por email.
     */
    @POST
    @Path("/invite")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response inviteUser(@Valid EmailDTO emailDTO, @HeaderParam("token") String adminToken) {
        verifier.verifyAdmin(adminToken);
        usersBean.requestRegistration(emailDTO.getEmail());
        return Response.ok("{\"message\":\"Convite enviado com sucesso para " + emailDTO.getEmail() + "\"}").build();
    }

    /**
     * POST /users/register -> Conclui o registo.
     */
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response completeRegistration(@QueryParam("token") String token, @Valid UserDTO userDTO) {
        if (token == null || token.isEmpty()) {
            return Response.status(400).entity("{\"error\":\"Token de convite em falta.\"}").build();
        }
        usersBean.completeRegistration(token, userDTO);
        return Response.status(Response.Status.CREATED)
                .entity("{\"message\":\"Conta criada com sucesso! Já pode fazer login.\"}")
                .build();
    }

    @GET
    @Path("/me")
    public Response getMyProfile(@HeaderParam("token") String token) {
        verifier.verifyUser(token);
        UserDTO user = usersBean.getUserDTOByToken(token);
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(user).build();
    }

    @PUT
    @Path("/me")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putEditMyProfile(@HeaderParam("token") String token, @Valid UserUpdateDTO userDTO) {
        verifier.verifyUser(token);
        usersBean.putEditOwnUser(token, userDTO);
        return Response.ok("{\"message\":\"Perfil atualizado com sucesso\"}").build();
    }

    @GET
    public Response getAllUsers(@HeaderParam("token") String token, @QueryParam("search") String search) {
        UserEntity requester = verifier.verifyUser(token);
        List<UserBaseDTO> users;
        if (requester.getUserRole() == UserRoles.ADMIN) {
            users = usersBean.getAllUsers(search);
        } else {
            users = usersBean.getAllActiveUsers();
        }
        return Response.ok(users).build();
    }

    @GET
    @Path("/{id:[0-9]+}")
    public Response getUserById(@PathParam("id") Long id, @HeaderParam("token") String token) {
        verifier.verifyAdmin(token);
        UserBaseDTO u = usersBean.getUserBaseDTOById(id);
        if (u == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(u).build();
    }

    @PATCH
    @Path("/{id:[0-9]+}/deactivate")
    public Response deactivateUser(@PathParam("id") Long id, @HeaderParam("token") String token) {
        verifier.verifyAdmin(token);
        usersBean.softDeleteUser(id);
        return Response.ok("{\"message\":\"Utilizador desativado.\"}").build();
    }

    @PATCH
    @Path("/{id:[0-9]+}/activate")
    public Response activateUser(@PathParam("id") Long id, @HeaderParam("token") String token) {
        verifier.verifyAdmin(token);
        usersBean.softUnDeleteUser(id);
        return Response.ok("{\"message\":\"Utilizador reativado.\"}").build();
    }

    @DELETE
    @Path("/{id:[0-9]+}")
    public Response deleteUser(@PathParam("id") Long id, @HeaderParam("token") String token) {
        verifier.verifyAdmin(token);
        usersBean.deleteUser(id);
        return Response.ok("{\"message\":\"Utilizador removido permanentemente.\"}").build();
    }

    @GET
    @Path("/username/{username}")
    public Response getUserByUsername(@PathParam("username") String username) {
        UserBaseDTO user = usersBean.getUserBaseDTOByUsername(username);
        return Response.ok(user).build();
    }

    @POST
    @Path("/forgot-password")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response forgotPassword(@Valid EmailDTO emailDTO) {
        usersBean.requestPasswordReset(emailDTO.getEmail());
        return Response.ok("{\"message\":\"Se o email existir, receberá um link de recuperação.\"}").build();
    }

    @POST
    @Path("/reset-password")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response resetPassword(@QueryParam("token") String token, LoginDTO loginDTO) {
        if (token == null || loginDTO.getPassword() == null) {
            throw new WebApplicationException("Token ou Password em falta.", 400);
        }
        usersBean.resetPassword(token, loginDTO.getPassword());
        return Response.ok("{\"message\":\"Password atualizada com sucesso.\"}").build();
    }
}