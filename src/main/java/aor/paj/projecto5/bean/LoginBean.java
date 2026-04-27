package aor.paj.projecto5.bean;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import aor.paj.projecto5.dto.LoginDTO;
import aor.paj.projecto5.dto.LoginResponseDTO;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.Serial;
import java.io.Serializable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bean responsável pela lógica de autenticação e gestão de sessão (Stateless).
 * Alterado para @RequestScoped para seguir as boas práticas de APIs REST.
 */
@RequestScoped
public class LoginBean implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(LoginBean.class);

    @Inject
    private UsersBean usersBean;

    @Inject
    private TokenBean tokenBean;

    public LoginBean() {}

    /**
     * Autentica o utilizador e gera um token de acesso.
     * @param loginDTO DTO com username e password.
     * @throws WebApplicationException 401 se as credenciais forem inválidas ou conta inativa.
     * @return LoginResponseDTO com os dados básicos e o token gerado.
     */
    public LoginResponseDTO login(LoginDTO loginDTO) {
        // O UsersBean já valida password e se o utilizador está em softDelete
        LoginResponseDTO response = usersBean.authenticateUser(loginDTO);

        if (response == null) {
            logger.warn("Tentativa de login falhada para o utilizador: " + loginDTO.getUsername());
            // Lançamos a exceção aqui. O GenericExceptionMapper tratará de enviar o JSON ao React.
            throw new WebApplicationException(
                    "Credenciais inválidas ou conta desativada.",
                    Response.Status.UNAUTHORIZED
            );
        }

        logger.info("Utilizador autenticado com sucesso: " + loginDTO.getUsername() + " (Perfil: " + response.getUserRole() + ")");
        return response;
    }

    /**
     * Finaliza a sessão do utilizador invalidando o seu token na base de dados.
     * @param token O valor do token enviado no Header.
     * @return true se o token foi invalidado com sucesso, false caso contrário.
     */
    public boolean logout(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        // Invalida o token no TokenBean -> TokenDao
        boolean success = tokenBean.invalidateToken(token);
        if (success) {
            logger.info("Sessão encerrada com sucesso (Token invalidado: " + token + ")");
        }
        return success;
    }
}