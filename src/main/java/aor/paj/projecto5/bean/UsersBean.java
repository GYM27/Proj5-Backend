package aor.paj.projecto5.bean;

import aor.paj.projecto5.dao.UserDao;
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
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * O meu EJB principal para a gestão de Utilizadores (Stateless porque não preciso 
 * de guardar estado do cliente entre chamadas, o que poupa memória ao servidor).
 * 
 * Foi aqui que decidi centralizar toda a lógica: desde o envio do convite inicial 
 * por email (pré-registo) até às edições de perfil (com as proteções para não deixarem 
 * alterar usernames ou passwords indevidamente).
 */
@Stateless
public class UsersBean implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(UsersBean.class);

    @Inject
    private UserDao userDao;

    @Inject
    private TokenBean tokenBean;

    @Inject
    private ConfirmationTokenBean confirmationTokenBean;

    @Inject
    private NotificationBean notificationBean;

    // =================================================================================
    // MÉTODOS DE MAPEAMENTO (DRY - DON'T REPEAT YOURSELF)
    // =================================================================================

    /**
     * SENTIDO: DB -> FRONTEND
     * Criei este método auxiliar para não ter de repetir código sempre que preciso 
     * de converter uma entidade para DTO. Mapeia apenas os dados seguros/públicos.
     *
     * @param entity A entidade que fui buscar à base de dados.
     * @param dto O DTO que vou preencher (pode ser UserBaseDTO ou o UserDTO completo).
     */
    private void fillBaseData(UserEntity entity, UserBaseDTO dto) {
        dto.setId(entity.getId());
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setUsername(entity.getUsername());
        dto.setEmail(entity.getEmail());
        dto.setCellphone(entity.getContact());
        dto.setPhotoUrl(entity.getPhoto());

        if (entity.getUserRole() != null) {
            dto.setRole(entity.getUserRole().name());
        }
        if (entity.getState() != null) {
            dto.setState(entity.getState().name());
        }
    }

    /**
     * SENTIDO: FRONTEND -> DB
     * Mapeia os dados recebidos de um DTO para uma entidade UserEntity.
     * Centraliza a atribuição de campos obrigatórios para evitar falhas de persistência.
     *
     * @param dto O DTO contendo os dados submetidos pelo utilizador.
     * @param entity A entidade alvo a ser preenchida/atualizada.
     */
    private void mapDtoToEntity(UserBaseDTO dto, UserEntity entity) {
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setEmail(dto.getEmail());
        entity.setContact(dto.getCellphone());
        entity.setPhoto(dto.getPhotoUrl());
        entity.setUsername(dto.getUsername());
    }

    // =================================================================================
    // CONVERSORES PÚBLICOS
    // =================================================================================

    /**
     * Converte uma UserEntity para UserBaseDTO (perfil público/listagens).
     *
     * @param entity A entidade a converter.
     * @return O DTO preenchido.
     */
    public UserBaseDTO convertToUserBaseDTO(UserEntity entity) {
        if (entity == null) return null;
        UserBaseDTO dto = new UserBaseDTO();
        fillBaseData(entity, dto);
        return dto;
    }

    /**
     * Converte uma UserEntity para UserDTO (inclui dados sensíveis como password).
     *
     * @param entity A entidade a converter.
     * @return O DTO completo preenchido.
     */
    public UserDTO convertToUserDTO(UserEntity entity) {
        if (entity == null) return null;
        UserDTO dto = new UserDTO();
        fillBaseData(entity, dto);
        // REMOVIDO: Nunca enviamos a password (nem o hash) para o frontend por segurança.
        // Se o utilizador quiser mudar a password, o DTO de update tratará disso se o campo for preenchido.
        return dto;
    }

    // =================================================================================
    // FLUXO DE REGISTO EM DOIS PASSOS (PRÉ-REGISTO)
    // =================================================================================

    @Inject
    private EmailBean emailBean;

    /**
     * PASSO 1 DO REGISTO: O Admin pede para convidar alguém.
     * Em vez de criar logo o utilizador na BD, preferi gerar um token seguro 
     * e enviar um link por email. Assim garanto que o email é real e válido.
     *
     * @param email O email que vou convidar.
     * @throws WebApplicationException Se o email já existir, bloqueio logo (409).
     */
    public void requestRegistration(String email) {
        if (userDao.findUserByEmail(email) != null) {
            throw new WebApplicationException("Este email já se encontra registado.", 409);
        }

        // Cria token associado apenas ao email
        String token = confirmationTokenBean.createTokenForEmail(email);
        
        // Constrói o link do frontend (ajusta o URL base se necessário)
        // Por norma em dev é localhost:5173, ajusta o URL conforme o teu ambiente.
        String frontendUrl = "http://localhost:5173"; 
        String registerLink = frontendUrl + "/register?token=" + token + "&email=" + email;
        
        // Envia o email de forma assíncrona
        String htmlBody = "<h1>Convite de Registo</h1>"
                + "<p>Foste convidado para a plataforma Bridge CRM.</p>"
                + "<p>Clica no link abaixo para concluíres o teu registo (válido por 24 horas):</p>"
                + "<a href='" + registerLink + "'>" + registerLink + "</a>";
                
        emailBean.sendEmail(email, "Convite para Registo - Bridge CRM", htmlBody);
        
        logger.info("Convite de registo enviado com sucesso para: " + email);
    }

    /**
     * PASSO 2: Conclui o registo após a validação do token de email.
     * Cria a entidade UserEntity já com estado ACTIVE.
     *
     * @param tokenString O token recebido por email.
     * @param userDTO Os dados completos do formulário de registo.
     * @throws WebApplicationException Se o token for inválido, o email não coincidir ou o username já existir.
     */
    public void completeRegistration(String tokenString, UserDTO userDTO) {
        ConfirmationTokenEntity tokenEntity = confirmationTokenBean.validateToken(tokenString);

        if (tokenEntity == null) {
            throw new WebApplicationException("Token de confirmação inválido ou expirado.", 401);
        }

        if (!tokenEntity.getEmail().equalsIgnoreCase(userDTO.getEmail())) {
            throw new WebApplicationException("O email do formulário não corresponde ao email do convite.", 403);
        }

        if (userDao.findUserByUsername(userDTO.getUsername()) != null) {
            throw new WebApplicationException("O nome de utilizador já está em uso.", 409);
        }

        UserEntity newUser = new UserEntity();
        mapDtoToEntity(userDTO, newUser);
        newUser.setPassword(PasswordUtils.hashPassword(userDTO.getPassword())); // Hashing no registo

        // Como o email foi validado pelo token, a conta nasce ACTIVE
        newUser.setState(UserState.ACTIVE);
        newUser.setUserRole(UserRoles.NORMAL);

        userDao.persist(newUser);

        logger.info("Registo de novo utilizador concluído com sucesso para: " + newUser.getUsername() + " (E-mail: " + newUser.getEmail() + ")");

        // Envia notificação para todos os administradores
        List<UserEntity> admins = userDao.findUsersByRole(UserRoles.ADMIN);
        for (UserEntity admin : admins) {
            notificationBean.createNotification(admin, aor.paj.projecto5.utils.NotificationType.SYSTEM, "Novo utilizador registado: " + newUser.getUsername());
        }

        // Remove o token para não ser reutilizado
        confirmationTokenBean.deleteToken(tokenEntity);
    }

    // =================================================================================
    // GESTÃO E CONSULTA DE PERFIS
    // =================================================================================

    // =================================================================================
    // MÉTODOS DE CONSULTA (LEITURA)
    // =================================================================================

    /**
     * Obtém os dados base (perfil público/admin) de um utilizador pelo seu ID.
     * Utilizado para listagens e visualização de perfis por administradores.
     *
     * @param id O identificador único do utilizador.
     * @return O DTO correspondente, ou null se a entidade não existir na base de dados.
     */
    public UserBaseDTO getUserBaseDTOById(Long id) {
        return convertToUserBaseDTO(userDao.find(id));
    }

    /**
     * Obtém os dados base de um utilizador pesquisando pelo seu username.
     *
     * @param username O nome de utilizador a procurar.
     * @return O DTO correspondente.
     * @throws WebApplicationException com status 404 se o utilizador não for encontrado.
     */
    public UserBaseDTO getUserBaseDTOByUsername(String username) {
        UserEntity entity = userDao.findUserByUsername(username);
        if (entity == null) {
            throw new WebApplicationException("Utilizador não encontrado", 404);
        }
        return convertToUserBaseDTO(entity);
    }

    /**
     * Obtém os dados completos (incluindo campos privados) do utilizador autenticado.
     * Este método é acedido através do token de sessão atual.
     *
     * @param token O token de sessão enviado no cabeçalho (header) do pedido.
     * @return O UserDTO completo pertencente ao dono do token.
     */
    public UserDTO getUserDTOByToken(String token) {
        UserEntity user = tokenBean.getUserEntityByToken(token);
        return convertToUserDTO(user);
    }

    /**
     * Edição do meu próprio perfil.
     * Aqui garanti que o utilizador nunca consegue "injetar" um novo username 
     * nem alterar os seus privilégios (cargo). A password só muda se ele preencher algo.
     *
     * @param token O meu token de sessão (para saber quem sou).
     * @param userDTO Os dados novos que enviei no formulário do Frontend.
     */
    public void putEditOwnUser(String token, aor.paj.projecto5.dto.UserUpdateDTO userDTO) {
        UserEntity user = tokenBean.getUserEntityByToken(token);
        if (user == null) throw new WebApplicationException("Sessão inválida", 401);

        // Validação de email duplicado
        UserEntity otherEmail = userDao.findUserByEmail(userDTO.getEmail());
        if (otherEmail != null && !otherEmail.getId().equals(user.getId())) {
            throw new WebApplicationException("Email já em uso por outro utilizador.", 409);
        }

        // Validação de contacto duplicado (CRÍTICO: evita erro 500 da BD)
        UserEntity otherContact = userDao.findUserByContact(userDTO.getCellphone());
        if (otherContact != null && !otherContact.getId().equals(user.getId())) {
            throw new WebApplicationException("Este contacto já está associado a outra conta.", 409);
        }

        // Atualização de campos permitidos
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEmail(userDTO.getEmail());
        user.setContact(userDTO.getCellphone());
        user.setPhoto(userDTO.getPhotoUrl());

        // Username NÃO é atualizado (regra de negócio: imutável após registo)

        // Só atualiza a password se o utilizador tiver preenchido o campo
        if (userDTO.getPassword() != null && !userDTO.getPassword().isBlank()) {
            user.setPassword(PasswordUtils.hashPassword(userDTO.getPassword())); // Hashing na edição de perfil
        }

        // EFETIVA A GRAVAÇÃO (CRÍTICO): Sem o merge, as alterações podem não ser persistidas.
        userDao.merge(user);
        
        logger.info("Utilizador " + user.getUsername() + " atualizou o seu próprio perfil.");
    }

    /**
     * Edição por parte do Admin (Super-Edição).
     * Como Admin, eu posso mudar quase tudo (incluindo o Cargo e Estado), 
     * mas decidi cortar o acesso à alteração da password por questões de privacidade.
     *
     * @param id Quem eu estou a editar.
     * @param dto Os novos dados (vindos do formulário de Admin).
     */
    public void putEditUser(Long id, UserBaseDTO dto) {
        UserEntity user = userDao.find(id);
        if (user == null) throw new WebApplicationException("Utilizador não encontrado", 404);

        UserEntity other = userDao.findUserByEmail(dto.getEmail());
        if (other != null && !other.getId().equals(id)) {
            throw new WebApplicationException("Email já associado a outra conta.", 409);
        }

        // Atualização administrativa limitada
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setContact(dto.getCellphone());
        user.setPhoto(dto.getPhotoUrl());

        // Admin pode alterar Role e State
        if (dto.getRole() != null) {
            user.setUserRole(UserRoles.valueOf(dto.getRole()));
        }
        if (dto.getState() != null) {
            user.setState(UserState.valueOf(dto.getState()));
        }
        
        // NOTA: Password e Username são ignorados nesta operação por segurança.
        
        logger.info("Perfil do utilizador (ID: " + id + ") foi atualizado administrativamente.");
    }

    /**
     * Desativa logicamente um utilizador (Estado DISABLED).
     *
     * @param id ID do utilizador.
     */
    public void softDeleteUser(Long id) {
        UserEntity user = userDao.find(id);
        if (user == null) throw new WebApplicationException("Não encontrado", 404);
        user.setState(UserState.DISABLED);
        logger.info("Utilizador (ID: " + id + ") foi desativado (eliminação lógica).");
    }

    /**
     * Reativa um utilizador (Estado ACTIVE).
     *
     * @param id ID do utilizador.
     */
    public void softUnDeleteUser(Long id) {
        UserEntity user = userDao.find(id);
        if (user == null) throw new WebApplicationException("Não encontrado", 404);
        user.setState(UserState.ACTIVE);
        logger.info("Utilizador (ID: " + id + ") foi reativado.");
    }

    /**
     * Remove permanentemente o utilizador e transfere os seus dados para o utilizador de sistema.
     *
     * @param id ID do utilizador a remover fisicamente.
     */
    public void deleteUser(Long id) {
        UserEntity userToDelete = userDao.find(id);
        if (userToDelete == null) throw new WebApplicationException("Não encontrado", 404);

        UserEntity systemUser = userDao.findUserByUsername("deleted_user");
        userDao.transferOwnership(userToDelete, systemUser);
        userDao.hardDelete(id);
        logger.info("Utilizador (ID: " + id + ") foi eliminado permanentemente e os dados foram transferidos para o utilizador de sistema.");
    }

    /**
     * Autentica o utilizador verificando credenciais e se o estado é ACTIVE.
     *
     * @param loginDTO Credenciais (username e password).
     * @return LoginResponseDTO com token se sucesso; null caso contrário.
     */
    public LoginResponseDTO authenticateUser(LoginDTO loginDTO) {
        if (loginDTO == null || loginDTO.getUsername() == null) return null;

        UserEntity userEntity = userDao.findUserByUsername(loginDTO.getUsername());
        
        // Uso o PasswordUtils para comparar o texto limpo com o hash guardado
        if (userEntity != null &&
                PasswordUtils.checkPassword(loginDTO.getPassword(), userEntity.getPassword()) &&
                userEntity.getState() == UserState.ACTIVE) {

            String token = tokenBean.generateNewToken(userEntity);

            return new LoginResponseDTO(
                    userEntity.getId(),
                    userEntity.getFirstName(),
                    userEntity.getLastName(),
                    userEntity.getUsername(),
                    userEntity.getEmail(),
                    userEntity.getUserRole(),
                    token,
                    userEntity.getPhoto()
            );
        }
        return null;
    }

    /**
     * Obtém a lista completa de utilizadores ativos e inativos, exceto o utilizador de sistema.
     *
     * @return Lista de UserBaseDTO.
     */
    public List<UserBaseDTO> getAllUsers() {
        List<UserEntity> entities = userDao.findAll();
        List<UserBaseDTO> result = new ArrayList<>();

        for (UserEntity u : entities) {
            if (!u.getUsername().equals("deleted_user")) {
                result.add(convertToUserBaseDTO(u));
            }
        }
        return result;
    }

    /**
     * Obtém apenas os utilizadores ativos para o chat.
     */
    public List<UserBaseDTO> getAllActiveUsers() {
        // Procuramos todos os utilizadores
        List<UserEntity> entities = userDao.findAll();
        List<UserBaseDTO> result = new ArrayList<>();

        for (UserEntity u : entities) {
            // Regra: Aparece se (está Ativo) OU (é Admin e não está Desativado)
            boolean isActive = u.getState() == UserState.ACTIVE;
            boolean isAdmin = u.getUserRole() != null && u.getUserRole().name().equals("ADMIN");
            boolean isNonDisabledAdmin = isAdmin && u.getState() != UserState.DISABLED;
            
            if ((isActive || isNonDisabledAdmin) && !u.getUsername().equals("deleted_user")) {
                result.add(convertToUserBaseDTO(u));
            }
        }
        return result;
    }

    // =================================================================================
    // RECUPERAÇÃO DE PASSWORD
    // =================================================================================

    /**
     * PASSO 1: Inicia o processo de recuperação de password.
     * Gera um token de segurança associado ao email do utilizador.
     *
     * @param email O email da conta a recuperar.
     */
    public void requestPasswordReset(String email) {
        UserEntity user = userDao.findUserByEmail(email);

        // Segurança: Se o email não existir, não dizemos "Erro".
        // Dizemos apenas que o email foi enviado para evitar que hackers saibam quem tem conta.
        if (user != null && user.getState() == UserState.ACTIVE) {
            String token = confirmationTokenBean.createTokenForEmail(email);
            
            String frontendUrl = "http://localhost:5173"; 
            String resetLink = frontendUrl + "/reset-password?token=" + token + "&email=" + email;
            
            String htmlBody = "<h1>Recuperação de Password</h1>"
                    + "<p>Recebemos um pedido para recuperar a password da tua conta.</p>"
                    + "<p>Clica no link abaixo para definires uma nova password (válido por 24 horas):</p>"
                    + "<a href='" + resetLink + "'>" + resetLink + "</a>"
                    + "<p>Se não solicitaste esta recuperação, podes ignorar este email.</p>";
                    
            emailBean.sendEmail(email, "Recuperação de Password - Bridge CRM", htmlBody);
            logger.info("Email de recuperação de password enviado para: " + email);
        }
    }

    /**
     * PASSO 2: Define uma nova password usando o token de validação.
     *
     * @param tokenString O token recebido por email.
     * @param newPassword A nova password escolhida pelo utilizador.
     * @throws WebApplicationException 401 se o token for inválido/expirado.
     */
    public void resetPassword(String tokenString, String newPassword) {
        // 1. Valida o token
        ConfirmationTokenEntity tokenEntity = confirmationTokenBean.validateToken(tokenString);
        if (tokenEntity == null) {
            throw new WebApplicationException("O link de recuperação é inválido ou já expirou.", 401);
        }

        // 2. Encontra o utilizador pelo email guardado no token
        UserEntity user = userDao.findUserByEmail(tokenEntity.getEmail());
        if (user == null) {
            throw new WebApplicationException("Utilizador não encontrado.", 404);
        }

        // 3. Atualiza a password com hash
        user.setPassword(PasswordUtils.hashPassword(newPassword));
        userDao.merge(user);

        // 4. Limpa o token para não ser usado duas vezes
        confirmationTokenBean.deleteToken(tokenEntity);
    }
}