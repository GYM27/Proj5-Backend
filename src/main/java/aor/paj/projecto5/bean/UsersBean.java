package aor.paj.projecto5.bean;

import aor.paj.projecto5.dao.UserDao;
import aor.paj.projecto5.dao.ConfirmationTokenDao;
import aor.paj.projecto5.dto.LoginDTO;
import aor.paj.projecto5.dto.LoginResponseDTO;
import aor.paj.projecto5.dto.UserBaseDTO;
import aor.paj.projecto5.dto.UserDTO;
import aor.paj.projecto5.entity.ConfirmationTokenEntity;
import aor.paj.projecto5.entity.UserEntity;
import aor.paj.projecto5.utils.PasswordUtils;
import aor.paj.projecto5.utils.MessageUtils;
import aor.paj.projecto5.utils.UserRoles;
import aor.paj.projecto5.utils.UserState;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import aor.paj.projecto5.websocket.ChatEndpoint;

@Stateless
public class UsersBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LogManager.getLogger(UsersBean.class);

    @Inject
    private UserDao userDao;

    @Inject
    private TokenBean tokenBean;

    @Inject
    private ConfirmationTokenDao confirmationTokenDao;

    @Inject
    private NotificationBean notificationBean;

    @Inject
    private EmailBean emailBean;

    @Inject
    private AuditLogBean auditLogBean;

    @Inject
    private ChatEndpoint chatEndpoint;

    // --- Métodos de Apoio ---

    private void fillBaseData(UserEntity entity, UserBaseDTO dto) {
        dto.setId(entity.getId());
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setUsername(entity.getUsername());
        dto.setEmail(entity.getEmail());
        dto.setCellphone(entity.getContact());
        dto.setPhotoUrl(entity.getPhoto());
        if (entity.getUserRole() != null) dto.setRole(entity.getUserRole().name());
        if (entity.getState() != null) dto.setState(entity.getState().name());
        dto.setLanguage(entity.getLanguage());
        
        // Verifica se o utilizador está online via WebSocket (proteção contra injeção falhada)
        if (chatEndpoint != null) {
            dto.setOnline(chatEndpoint.isUserOnline(entity.getId()));
        } else {
            dto.setOnline(false);
        }
    }

    private void mapDtoToEntity(UserBaseDTO dto, UserEntity entity) {
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setEmail(dto.getEmail());
        entity.setContact(dto.getCellphone());
        entity.setPhoto(dto.getPhotoUrl());
        entity.setUsername(dto.getUsername());
        if (dto.getLanguage() != null) entity.setLanguage(dto.getLanguage());
    }

    public UserBaseDTO convertToUserBaseDTO(UserEntity entity) {
        if (entity == null) return null;
        UserBaseDTO dto = new UserBaseDTO();
        fillBaseData(entity, dto);
        return dto;
    }

    public UserDTO convertToUserDTO(UserEntity entity) {
        if (entity == null) return null;
        UserDTO dto = new UserDTO();
        fillBaseData(entity, dto);
        return dto;
    }

    // =================================================================================
    // FLUXO DE REGISTO (CONVITE)
    // =================================================================================

    public void requestRegistration(String email) {
        try {
            logger.info("A processar pedido de convite para: " + email);
            
            UserEntity existingUser = userDao.findUserByEmail(email);
            if (existingUser != null && existingUser.getState() != UserState.PENDING) {
                throw new WebApplicationException("Este email já se encontra registado e ativo.", 409);
            }

            // Se não existe utilizador, criamos um fantasma PENDING
            if (existingUser == null) {
                existingUser = new UserEntity();
                existingUser.setEmail(email);
                existingUser.setState(UserState.PENDING);
                existingUser.setUserRole(UserRoles.NORMAL);
                // Placeholder temporário para username único (usamos o email)
                existingUser.setUsername(email); 
                userDao.persist(existingUser);
            }

            String tokenString = UUID.randomUUID().toString();
            
            ConfirmationTokenEntity existingToken = confirmationTokenDao.findTokenByEmail(email);
            if (existingToken != null) {
                existingToken.setToken(tokenString);
                existingToken.setExpiresAt(LocalDateTime.now().plusHours(24));
                confirmationTokenDao.merge(existingToken);
            } else {
                ConfirmationTokenEntity newToken = new ConfirmationTokenEntity(tokenString, email, 24);
                confirmationTokenDao.persist(newToken);
            }
            
            // LOG DE AUDITORIA
            auditLogBean.logSystemAction("INVITE_SENT", "Convite enviado para: " + email);

            String frontendUrl = "http://localhost:5173"; 
            String registerLink = frontendUrl + "/register?token=" + tokenString + "&email=" + email;
            String htmlBody = "<h1>Convite de Registo</h1><p>Clica no link para concluir:</p><a href='" + registerLink + "'>" + registerLink + "</a>";
                    
            emailBean.sendEmail(email, "Convite para Registo", htmlBody);
            
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("ERRO FATAL NO CONVITE: ", e);
            throw new WebApplicationException("Erro interno ao processar convite.", 500);
        }
    }

    public void completeRegistration(String tokenString, UserDTO userDTO) {
        ConfirmationTokenEntity tokenEntity = confirmationTokenDao.findTokenByString(tokenString);

        if (tokenEntity == null || tokenEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new WebApplicationException("Token inválido ou expirado.", 401);
        }

        // Procuramos o utilizador PENDING que foi criado no convite
        UserEntity user = userDao.findUserByEmail(tokenEntity.getEmail());
        if (user == null || user.getState() != UserState.PENDING) {
            throw new WebApplicationException("Utilizador não encontrado ou já ativado.", 404);
        }

        // Validamos se o username novo já existe em outro utilizador
        if (userDao.findUserByUsername(userDTO.getUsername()) != null && !user.getUsername().equals(userDTO.getUsername())) {
            throw new WebApplicationException(MessageUtils.getMessage("error.username_exists", user.getLanguage()), 409);
        }

        // Preenchemos os dados finais
        mapDtoToEntity(userDTO, user);
        user.setPassword(PasswordUtils.hashPassword(userDTO.getPassword()));
        user.setState(UserState.ACTIVE);

        userDao.merge(user);
        confirmationTokenDao.remove(tokenEntity);
        
        // LOG DE AUDITORIA
        auditLogBean.logAction(user, "USER_REGISTERED", "Novo utilizador concluiu o registo.");
    }

    public LoginResponseDTO authenticateUser(LoginDTO loginDTO) {
        UserEntity userEntity = userDao.findUserByUsername(loginDTO.getUsername());
        if (userEntity != null &&
                PasswordUtils.checkPassword(loginDTO.getPassword(), userEntity.getPassword()) &&
                userEntity.getState() == UserState.ACTIVE) {

            String token = tokenBean.generateNewToken(userEntity);
            
            // LOG DE AUDITORIA: Sucesso no Login
            auditLogBean.logAction(userEntity, "LOGIN", "Sessão iniciada com sucesso.");

            return new LoginResponseDTO(
                    userEntity.getId(), userEntity.getFirstName(), userEntity.getLastName(),
                    userEntity.getUsername(), userEntity.getEmail(), userEntity.getUserRole(),
                    token, userEntity.getPhoto(), userEntity.getLanguage()
            );
        } else {
            // LOG DE AUDITORIA: Falha no Login
            auditLogBean.logSystemAction("LOGIN_FAILED", "Tentativa de login falhada para o username: " + (loginDTO != null ? loginDTO.getUsername() : "desconhecido"));
        }
        return null;
    }

    public void deleteUser(Long id) {
        UserEntity userToDelete = userDao.find(id);
        if (userToDelete != null) {
            String targetUser = userToDelete.getUsername();
            UserEntity systemUser = userDao.findUserByUsername("deleted_user");
            userDao.transferOwnership(userToDelete, systemUser);
            userDao.hardDelete(id);
            
            // LOG DE AUDITORIA
            auditLogBean.logSystemAction("USER_DELETED", "Utilizador eliminado permanentemente: " + targetUser);
        }
    }

    public void softDeleteUser(Long id) {
        UserEntity user = userDao.find(id);
        if (user != null) {
            user.setState(UserState.DISABLED);
            userDao.merge(user);
            auditLogBean.logSystemAction("USER_DISABLED", "Utilizador desativado: " + user.getUsername());
        }
    }

    public void softUnDeleteUser(Long id) {
        UserEntity user = userDao.find(id);
        if (user != null) {
            user.setState(UserState.ACTIVE);
            userDao.merge(user);
            auditLogBean.logSystemAction("USER_ENABLED", "Utilizador reativado: " + user.getUsername());
        }
    }

    // =================================================================================
    // GESTÃO DE CONVITES (ADMIN)
    // =================================================================================

    /**
     * Retorna todos os utilizadores que foram convidados mas ainda não confirmaram.
     */
    public List<UserBaseDTO> getAllPendingInvites() {
        List<UserEntity> pendingUsers = userDao.findUsersByState(UserState.PENDING);
        List<UserBaseDTO> dtos = new ArrayList<>();
        for (UserEntity u : pendingUsers) {
            dtos.add(convertToUserBaseDTO(u));
        }
        return dtos;
    }

    /**
     * Cancela um convite: remove o utilizador PENDING e o respetivo token.
     */
    public void cancelInvitation(String email) {
        UserEntity user = userDao.findUserByEmail(email);
        if (user != null && user.getState() == UserState.PENDING) {
            // Remove o utilizador
            userDao.hardDelete(user.getId());
            
            // Remove o token associado
            ConfirmationTokenEntity token = confirmationTokenDao.findTokenByEmail(email);
            if (token != null) {
                confirmationTokenDao.remove(token);
            }
            
            auditLogBean.logSystemAction("INVITE_CANCELLED", "Convite cancelado para: " + email);
        }
    }

    /**
     * Reenvia um convite: renova o token e manda novo email.
     */
    public void resendInvitation(String email) {
        // O requestRegistration já trata de atualizar se o user for PENDING
        requestRegistration(email);
        auditLogBean.logSystemAction("INVITE_RESENT", "Convite reenviado para: " + email);
    }

    // --- Métodos de Listagem (Sem Log para não poluir) ---

    public UserBaseDTO getUserBaseDTOById(Long id) { return convertToUserBaseDTO(userDao.find(id)); }
    public UserBaseDTO getUserBaseDTOByUsername(String username) {
        UserEntity entity = userDao.findUserByUsername(username);
        if (entity == null) throw new WebApplicationException("Não encontrado", 404);
        return convertToUserBaseDTO(entity);
    }
    public UserDTO getUserDTOByToken(String token) {
        UserEntity user = tokenBean.getUserEntityByToken(token);
        return convertToUserDTO(user);
    }

    public void putEditOwnUser(String token, aor.paj.projecto5.dto.UserUpdateDTO userDTO) {
        UserEntity user = tokenBean.getUserEntityByToken(token);
        if (user == null) throw new WebApplicationException("Sessão inválida", 401);
        
        // Na edição própria, forçamos a validação da password antiga se quiser mudar a nova
        updateUserData(user, userDTO, true);
        
        userDao.merge(user);
        auditLogBean.logAction(user, "PROFILE_UPDATE", "O utilizador atualizou os seus dados de perfil.");
    }

    /**
     * Edição administrativa de utilizadores por ID.
     */
    public void updateUser(Long id, aor.paj.projecto5.dto.UserUpdateDTO userDTO) {
        UserEntity user = userDao.find(id);
        if (user == null) throw new WebApplicationException("Utilizador não encontrado", 404);

        // Na edição administrativa, NÃO validamos a password antiga
        updateUserData(user, userDTO, false);

        userDao.merge(user);
        auditLogBean.logSystemAction("ADMIN_USER_UPDATE", "Administrador atualizou o perfil de: " + user.getUsername());
    }

    /**
     * Lógica partilhada de mapeamento de DTO para Entity com hash de password.
     */
    private void updateUserData(UserEntity user, aor.paj.projecto5.dto.UserUpdateDTO userDTO, boolean isOwnUpdate) {
        // Validar conflitos de email se o email mudou
        UserEntity emailConflict = userDao.findUserByEmail(userDTO.getEmail());
        if (emailConflict != null && !emailConflict.getId().equals(user.getId())) {
            throw new WebApplicationException(MessageUtils.getMessage("error.email_exists", user.getLanguage()), 409);
        }

        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEmail(userDTO.getEmail());
        user.setContact(userDTO.getCellphone());
        user.setPhoto(userDTO.getPhotoUrl());
        
        // Atualizar idioma se fornecido
        if (userDTO.getLanguage() != null && !userDTO.getLanguage().isBlank()) {
            user.setLanguage(userDTO.getLanguage());
        }

        // SÓ permite mudar password se for a própria pessoa a editar
        if (isOwnUpdate && userDTO.getPassword() != null && !userDTO.getPassword().isBlank()) {
            // Verificar se forneceu a password atual
            if (userDTO.getCurrentPassword() == null || userDTO.getCurrentPassword().isBlank()) {
                throw new WebApplicationException(MessageUtils.getMessage("error.password_required", user.getLanguage()), 403);
            }
            // Validar se a password atual está correta
            if (!PasswordUtils.checkPassword(userDTO.getCurrentPassword(), user.getPassword())) {
                throw new WebApplicationException(MessageUtils.getMessage("error.password_incorrect", user.getLanguage()), 403);
            }
            user.setPassword(PasswordUtils.hashPassword(userDTO.getPassword()));
        }
    }

    public List<UserBaseDTO> getAllUsers(String search) {
        List<UserEntity> entities = userDao.findAll();
        List<UserBaseDTO> result = new ArrayList<>();
        String term = (search != null) ? search.toLowerCase().trim() : "";
        for (UserEntity u : entities) {
            if (u.getUsername().equals("deleted_user")) continue;
            if (term.isEmpty() || u.getUsername().toLowerCase().contains(term) || u.getEmail().toLowerCase().contains(term)) {
                result.add(convertToUserBaseDTO(u));
            }
        }
        return result;
    }

    public List<UserBaseDTO> getAllActiveUsers() {
        List<UserEntity> entities = userDao.findAll();
        List<UserBaseDTO> result = new ArrayList<>();
        for (UserEntity u : entities) {
            if (u.getState() == UserState.ACTIVE && !u.getUsername().equals("deleted_user")) {
                result.add(convertToUserBaseDTO(u));
            }
        }
        return result;
    }

    public void requestPasswordReset(String email) {
        UserEntity user = userDao.findUserByEmail(email);
        if (user != null && user.getState() == UserState.ACTIVE) {
            String tokenString = UUID.randomUUID().toString();
            ConfirmationTokenEntity existing = confirmationTokenDao.findTokenByEmail(email);
            if (existing != null) {
                existing.setToken(tokenString);
                existing.setExpiresAt(LocalDateTime.now().plusHours(24));
                confirmationTokenDao.merge(existing);
            } else {
                confirmationTokenDao.persist(new ConfirmationTokenEntity(tokenString, email, 24));
            }
            
            // Construção do Link (Ajustar para o URL final do teu Frontend)
            String resetLink = "http://localhost:5173/reset-password?token=" + tokenString;
            
            // Corpo do Email em HTML com Botão Estilizado e Link de Fallback
            String htmlBody = "<html><body>" +
                    "<div style='font-family: sans-serif; max-width: 600px; padding: 20px; border: 1px solid #ccc;'>" +
                    "<h2>Recuperação de Password</h2>" +
                    "<p>Recebemos um pedido de recuperação para a conta: <b>" + email + "</b></p>" +
                    "<p>Clica no botão abaixo para redefinir a tua password:</p>" +
                    "<div style='margin: 30px 0;'>" +
                    "<a href='" + resetLink + "' style='background: #007bff; color: #ffffff; padding: 15px 25px; text-decoration: none; border-radius: 4px; display: inline-block; font-weight: bold;'>REDEFINIR PASSWORD</a>" +
                    "</div>" +
                    "<p>Se o botão não funcionar, copia e cola o seguinte link no teu browser:</p>" +
                    "<p><a href='" + resetLink + "'>" + resetLink + "</a></p>" +
                    "<hr><p><small>Este link expira em 24 horas.</small></p>" +
                    "</div>" +
                    "</body></html>";

            logger.info("A enviar email de reset com o link: " + resetLink);
            auditLogBean.logAction(user, "PASSWORD_RESET_REQ", "Pedido de recuperação de password enviado.");
            emailBean.sendEmail(email, "Recuperação de Password - Bridge CRM", htmlBody);
        }
    }

    public void resetPassword(String tokenString, String newPassword) {
        ConfirmationTokenEntity tokenEntity = confirmationTokenDao.findTokenByString(tokenString);
        if (tokenEntity != null && !tokenEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            UserEntity user = userDao.findUserByEmail(tokenEntity.getEmail());
            if (user != null) {
                user.setPassword(PasswordUtils.hashPassword(newPassword));
                userDao.merge(user);
                confirmationTokenDao.remove(tokenEntity);
                auditLogBean.logAction(user, "PASSWORD_CHANGED", "Password alterada com sucesso via recuperação.");
            }
        }
    }
}