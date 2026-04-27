package aor.paj.projecto5.bean;

import aor.paj.projecto5.utils.UserState;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import aor.paj.projecto5.dao.LeadDao;
import aor.paj.projecto5.dao.UserDao;
import aor.paj.projecto5.dto.LeadDTO;
import aor.paj.projecto5.entity.LeadEntity;
import aor.paj.projecto5.entity.UserEntity;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * O meu EJB principal para gerir as Leads (Oportunidades de negócio).
 * Centralizei aqui todas as regras, desde a criação básica pelo vendedor até 
 * às operações de "Hard Delete" e "Esvaziar Lixeira" que só o Administrador pode fazer.
 */
@Stateless
public class LeadsBean implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(LeadsBean.class);

    @Inject
    LeadDao leadDao;

    @Inject
    TokenBean tokenBean;

    @Inject
    UserDao userDao;

    // =================================================================================
    // HELPERS (Mapeamento DTO <-> Entidade)
    // =================================================================================

    /**
     * Auxiliar para converter a LeadEntity que vem da Base de Dados num DTO limpo 
     * para enviar para o React. Tive o cuidado de extrair apenas o nome do "Owner" 
     * (dono da lead) em vez de enviar o objeto User inteiro, para não sobrecarregar o JSON.
     *
     * @param entity A lead tal como está na BD.
     * @return O DTO pronto a ser enviado pela API.
     */
    public LeadDTO entityToDTO(LeadEntity entity) {
        if (entity == null) return null;
        LeadDTO dto = new LeadDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitulo());
        dto.setDescription(entity.getDescricao());
        dto.setState(entity.getLeadState().getStateId());
        if (entity.getData() != null) {
            dto.setDate(entity.getData().toString());
        }
        dto.setSoftDeleted(entity.isSoftDeleted());

        // Aceder aos dados do Owner de forma segura
        if (entity.getOwner() != null) {
            // Extraímos apenas as Strings. Isto evita que o Jackson tente
            // serializar o objeto UserEntity completo.
            String firstName = entity.getOwner().getFirstName();
            String lastName = entity.getOwner().getLastName();

            dto.setFirstName(firstName);
            dto.setLastName(lastName);
            dto.setName(firstName + " " + lastName);
        }

        return dto;
    }

    /**
     * Converte um LeadDTO numa nova LeadEntity, associando-lhe um dono.
     *
     * @param dto Os dados submetidos pelo utilizador.
     * @param owner A entidade do utilizador que será o dono da Lead.
     * @return A nova entidade pronta a ser persistida.
     */
    private LeadEntity DTOToEntity(LeadDTO dto, UserEntity owner) {
        LeadEntity entity = new LeadEntity();
        entity.setTitulo(dto.getTitle());
        entity.setDescricao(dto.getDescription());
        entity.setLeadState(dto.getStateEnum());
        entity.setOwner(owner);
        entity.setSoftDeleted(dto.isSoftDeleted());
        return entity;
    }

    /**     * Converte uma lista de Entidades numa lista de DTOs.
     *
     * @param entities Lista de LeadEntity.
     * @return Lista de LeadDTO correspondente.
     */
    private List<LeadDTO> toDTOList(List<LeadEntity> entities) {
        if (entities == null) return Collections.emptyList();
        return entities.stream()
                .map(this::entityToDTO)
                .collect(Collectors.toList());
    }

    // =================================================================================
    // FUNCIONALIDADES DE USER
    // =================================================================================

    /**
     * Cria uma nova lead associada ao utilizador autenticado pelo token.
     *
     * @param token Token de sessão do utilizador.
     * @param dto Dados da nova lead a criar.
     * @return O DTO da lead recém-criada (com ID gerado).
     */
    public LeadDTO addLead(String token, LeadDTO dto) {
        UserEntity owner = tokenBean.getUserEntityByToken(token);
        LeadEntity newLead = DTOToEntity(dto, owner);

        leadDao.persist(newLead);
        
        logger.info("Lead '" + newLead.getTitulo() + "' (ID: " + newLead.getId() + ") criada com sucesso pelo utilizador: " + owner.getUsername() + " (Token: " + token + ")");

        return entityToDTO(newLead);
    }

    /**
     * O método que alimenta o meu Kanban no Frontend!
     * Pega no token do utilizador, descobre quem ele é, e vai buscar as leads dele.
     * Tem um truque: se o React pedir 'softDeleted=true', devolvo as leads da lixeira.
     *
     * @param token O token de sessão.
     * @param softDeleted Quero as leads ativas (false) ou as apagadas (true)?
     * @return A lista de leads pronta para o Kanban.
     */
    public List<LeadDTO> getLeadsByToken(String token, Boolean softDeleted) {
        UserEntity user = tokenBean.getUserEntityByToken(token);

        List<LeadEntity> entities;

        // 1. Se o React enviou softDeleted=true (User clicou no botão da lixeira)
        if (softDeleted != null && softDeleted) {
            entities = leadDao.getTrashLeadsByUserId(user.getId());
        } else {
            // 2. Comportamento normal (vai buscar as leads ativas)
            entities = leadDao.getLeadsByUserId(user.getId());
        }

        // 3. A base de dados já fez o filtro, só precisamos de converter para DTO
        return entities.stream()
                .map(this::entityToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtém uma lead específica através do seu ID.
     *
     * @param leadId O identificador único da lead.
     * @return O LeadDTO correspondente.
     */
    public LeadDTO getLeadById(Long leadId) {
        LeadEntity entity = leadDao.getLeadByLeadID(leadId);
        return entityToDTO(entity);
    }

    /**
     * Edita os dados base de uma lead existente (Título, Descrição e Estado).
     *
     * @param leadId O identificador único da lead a editar.
     * @param dto Os novos dados a aplicar.
     * @return O DTO da lead atualizada.
     */
    public LeadDTO editLead(Long leadId, LeadDTO dto) {
        LeadEntity lead = leadDao.getLeadByLeadID(leadId);

        lead.setTitulo(dto.getTitle());
        lead.setDescricao(dto.getDescription());
        lead.setLeadState(dto.getStateEnum());

        leadDao.merge(lead);
        
        logger.info("Lead (ID: " + leadId + ") foi editada. Novo Título: '" + dto.getTitle() + "', Estado: " + dto.getStateEnum());

        return entityToDTO(lead);
    }

    /**
     * Quando o utilizador clica em apagar no Frontend, eu não apago logo da BD!
     * Apenas mudo a flag 'softDeleted' para true, enviando a lead para a "Lixeira".
     * Assim, se foi um erro, o Administrador pode sempre recuperar.
     *
     * @param leadId O ID da lead a "apagar".
     */
    public void softDeleteLead(Long leadId) {
        LeadEntity lead = leadDao.getLeadByLeadID(leadId);
        if (lead != null) {
            lead.setSoftDeleted(true);
            leadDao.merge(lead);
            logger.info("Lead (ID: " + leadId + ") foi eliminada logicamente (movida para o lixo).");
        }
    }

    // =================================================================================
    // FUNCIONALIDADES DE ADMIN
    // =================================================================================

    /**
     * Restaura uma lead que estava na lixeira, voltando a marcá-la como ativa.
     *
     * @param leadId O identificador único da lead a restaurar.
     */
    public void restoreLead(Long leadId) {
        LeadEntity lead = leadDao.getLeadByLeadID(leadId);
        if (lead != null) {
            // Mudamos o estado para false para que ela volte a ser "Ativa"
            lead.setSoftDeleted(false);
            leadDao.merge(lead);
        }
    }


    /**
     * Lista leads globalmente com base em múltiplos filtros dinâmicos.
     * Exclusivo para administradores.
     *
     * @param stateId ID do estado da lead (opcional).
     * @param userId ID do dono da lead (opcional).
     * @param softDeleted Estado de eliminação lógica da lead (opcional).
     * @return Lista filtrada de LeadDTOs.
     */
    public List<LeadDTO> getLeadsWithFilters(Integer stateId, Long userId, Boolean softDeleted) {
        List<LeadEntity> entities = leadDao.findLeadsWithFilters(stateId, userId, softDeleted);
        return toDTOList(entities);
    }

    /**
     * Edição com privilégios totais de Administrador.
     * Permite alterar propriedades exclusivas, como recuperar uma lead diretamente na edição.
     *
     * @param leadId O identificador único da lead alvo.
     * @param dto Os novos dados a aplicar.
     * @return O DTO da lead resultante.
     * @throws WebApplicationException Se a lead não existir (404) ou os dados forem inválidos (400).
     */
    public LeadDTO adminSuperEdit(Long leadId, LeadDTO dto) {
        if (dto == null) throw new WebApplicationException("Dados de edição inválidos", 400);

        LeadEntity lead = leadDao.getLeadByLeadID(leadId);
        if (lead == null) throw new WebApplicationException("Lead não encontrada", 404);

        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            lead.setTitulo(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            lead.setDescricao(dto.getDescription());
        }
        if (dto.getState() > 0) {
            lead.setLeadState(dto.getStateEnum());
        }

        lead.setSoftDeleted(dto.isSoftDeleted());

        leadDao.merge(lead);
        return entityToDTO(lead);
    }

    /**
     * Administrador cria uma lead atribuindo-a diretamente a um utilizador específico.
     *
     * @param userId ID do utilizador destino (owner) que vai receber a lead.
     * @param dto Dados da nova lead a ser criada.
     * @return O DTO com a lead recém-criada.
     * @throws WebApplicationException 404 se o user não existir, 403 se o user não estiver ativo.
     */
    public LeadDTO addLeadToUser(Long userId, LeadDTO dto) {
        // 1. Busca o utilizador à base de dados
        UserEntity owner = userDao.find(userId);

        // 2. Validação 1: O utilizador existe?
        if (owner == null) {
            throw new WebApplicationException("Utilizador destino não encontrado.", 404);
        }

        // 3. Validação 2: A nova lógica de Estados
        // Impede o Admin de atribuir leads a contas DISABLED ou PENDING
        if (owner.getState() != UserState.ACTIVE) {
            throw new WebApplicationException("Não é possível atribuir leads: o utilizador encontra-se inativo ou pendente.", 403);
        }

        // 4. Criação e Persistência
        LeadEntity newLead = DTOToEntity(dto, owner);
        leadDao.persist(newLead);

        // 5. Retorna o DTO atualizado
        return entityToDTO(newLead);
    }

    /**
     * Operação perigosa reservada ao Admin: Hard Delete!
     * Aqui sim, a lead é removida permanentemente e fisicamente da tabela PostgreSQL.
     *
     * @param leadId O ID da lead a destruir.
     * @throws WebApplicationException 404 se a lead já não existir.
     */
    public void hardDeleteLead(Long leadId) {
        LeadEntity lead = leadDao.getLeadByLeadID(leadId);
        if (lead == null) {
            throw new WebApplicationException("Lead não encontrada para remoção.", 404);
        }
        // Homogéneo: Uso do leadDao.remove herdado do AbstractDao
        leadDao.remove(lead);
    }

    /**
     * Move todas as leads de um determinado utilizador para a lixeira de uma só vez.
     *
     * @param userId O identificador do dono das leads.
     * @return O número de registos alterados na base de dados.
     * @throws WebApplicationException 404 se o utilizador não existir.
     */
    public int softDeleteAllFromUser(Long userId) {
        if (userDao.find(userId) == null) throw new WebApplicationException("User not found", 404);
        return leadDao.bulkUpdateSoftDelete(userId, true);
    }

    /**
     * Restaura em massa todas as leads de um utilizador que se encontravam na lixeira.
     *
     * @param userId O identificador do dono das leads.
     * @return O número de registos alterados na base de dados.
     * @throws WebApplicationException 404 se o utilizador não existir.
     */
    public int undeleteAllFromUser(Long userId) {
        if (userDao.find(userId) == null) throw new WebApplicationException("User not found", 404);
        return leadDao.bulkUpdateSoftDelete(userId, false);
    }

    /**
     * Elimina fisicamente todas as leads da lixeira de um determinado utilizador.
     * Ação irreversível.
     *
     * @param userId O identificador do dono das leads na lixeira.
     * @return O número de registos permanentemente apagados.
     * @throws WebApplicationException 404 se o utilizador não existir.
     */
    public int emptyTrash(Long userId) {
        if (userDao.find(userId) == null) throw new WebApplicationException("User not found", 404);
        // Delega para o DAO a execução da query de remoção física
        return leadDao.emptyTrashByUserId(userId);
    }
}