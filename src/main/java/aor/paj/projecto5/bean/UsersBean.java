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
    }

    private void mapDtoToEntity(UserBaseDTO dto, UserEntity entity) {
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setEmail(dto.getEmail());
        entity.setContact(dto.getCellphone());
        entity.setPhoto(dto.getPhotoUrl());
        entity.setUsername(dto.getUsername());
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
            
            if (userDao.findUserByEmail(email) != null) {
                throw new WebApplicationException("Este email já se encontra registado.", 409);
            }

            String tokenString = UUID.randomUUID().toString();
            
            ConfirmationTokenEntity existing = confirmationTokenDao.findTokenByEmail(email);
            if (existing != null) {
                existing.setToken(tokenString);
                existing.setExpiresAt(LocalDateTime.now().plusHours(24));
                confirmationTokenDao.merge(existing);
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

        UserEntity newUser = new UserEntity();
        mapDtoToEntity(userDTO, newUser);
        newUser.setPassword(PasswordUtils.hashPassword(userDTO.getPassword()));
        newUser.setState(UserState.ACTIVE);
        newUser.setUserRole(UserRoles.NORMAL);

        userDao.persist(newUser);
        confirmationTokenDao.remove(tokenEntity);
        
        // LOG DE AUDITORIA
        auditLogBean.logAction(newUser, "USER_REGISTERED", "Novo utilizador concluiu o registo.");
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
                    token, userEntity.getPhoto()
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
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEmail(userDTO.getEmail());
        user.setContact(userDTO.getCellphone());
        user.setPhoto(userDTO.getPhotoUrl());
        if (userDTO.getPassword() != null && !userDTO.getPassword().isBlank()) {
            user.setPassword(PasswordUtils.hashPassword(userDTO.getPassword()));
        }
        userDao.merge(user);
        auditLogBean.logAction(user, "PROFILE_UPDATE", "O utilizador atualizou os seus dados de perfil.");
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
            auditLogBean.logAction(user, "PASSWORD_RESET_REQ", "Pedido de recuperação de password enviado.");
            emailBean.sendEmail(email, "Recuperação de Password", "Link de reset para " + email);
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