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
 * EJB para gestão de Leads com Auditoria integrada.
 * Restaurada a lista completa de métodos de Administrador.
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

    @Inject
    AuditLogBean auditLogBean;

    // --- Helpers ---

    public LeadDTO entityToDTO(LeadEntity entity) {
        if (entity == null) return null;
        LeadDTO dto = new LeadDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitulo());
        dto.setDescription(entity.getDescricao());
        dto.setState(entity.getLeadState().getStateId());
        if (entity.getData() != null) dto.setDate(entity.getData().toString());
        dto.setSoftDeleted(entity.isSoftDeleted());
        if (entity.getOwner() != null) {
            dto.setFirstName(entity.getOwner().getFirstName());
            dto.setLastName(entity.getOwner().getLastName());
            dto.setName(dto.getFirstName() + " " + dto.getLastName());
        }
        return dto;
    }

    private LeadEntity DTOToEntity(LeadDTO dto, UserEntity owner) {
        LeadEntity entity = new LeadEntity();
        entity.setTitulo(dto.getTitle());
        entity.setDescricao(dto.getDescription());
        entity.setLeadState(dto.getStateEnum());
        entity.setOwner(owner);
        entity.setSoftDeleted(dto.isSoftDeleted());
        return entity;
    }

    private List<LeadDTO> toDTOList(List<LeadEntity> entities) {
        if (entities == null) return Collections.emptyList();
        return entities.stream().map(this::entityToDTO).collect(Collectors.toList());
    }

    // --- Ações de Utilizador ---

    public LeadDTO addLead(String token, LeadDTO dto) {
        UserEntity owner = tokenBean.getUserEntityByToken(token);
        LeadEntity newLead = DTOToEntity(dto, owner);
        leadDao.persist(newLead);
        auditLogBean.logAction(owner, "CREATE_LEAD", "Criou a lead: " + newLead.getTitulo());
        return entityToDTO(newLead);
    }

    public List<LeadDTO> getLeadsByToken(String token, Boolean softDeleted) {
        UserEntity user = tokenBean.getUserEntityByToken(token);
        List<LeadEntity> entities = (softDeleted != null && softDeleted) 
                ? leadDao.getTrashLeadsByUserId(user.getId()) 
                : leadDao.getLeadsByUserId(user.getId());
        return toDTOList(entities);
    }

    public LeadDTO getLeadById(Long leadId) {
        return entityToDTO(leadDao.getLeadByLeadID(leadId));
    }

    public LeadDTO editLead(Long leadId, LeadDTO dto) {
        LeadEntity lead = leadDao.getLeadByLeadID(leadId);
        if (lead != null) {
            lead.setTitulo(dto.getTitle());
            lead.setDescricao(dto.getDescription());
            lead.setLeadState(dto.getStateEnum());
            leadDao.merge(lead);
            auditLogBean.logAction(lead.getOwner(), "UPDATE_LEAD", "Editou a lead: " + lead.getTitulo());
        }
        return entityToDTO(lead);
    }

    public void softDeleteLead(Long leadId) {
        LeadEntity lead = leadDao.getLeadByLeadID(leadId);
        if (lead != null) {
            lead.setSoftDeleted(true);
            leadDao.merge(lead);
            auditLogBean.logAction(lead.getOwner(), "DELETE_LEAD", "Moveu para a lixeira: " + lead.getTitulo());
        }
    }

    // --- Ações de Admin ---

    public void restoreLead(Long leadId) {
        LeadEntity lead = leadDao.getLeadByLeadID(leadId);
        if (lead != null) {
            lead.setSoftDeleted(false);
            leadDao.merge(lead);
            auditLogBean.logSystemAction("RESTORE_LEAD", "Restaurou a lead: " + lead.getTitulo());
        }
    }

    public List<LeadDTO> getLeadsWithFilters(Integer stateId, Long userId, Boolean softDeleted) {
        return toDTOList(leadDao.findLeadsWithFilters(stateId, userId, softDeleted));
    }

    public LeadDTO adminSuperEdit(Long leadId, LeadDTO dto) {
        if (dto == null) throw new WebApplicationException("Dados de edição inválidos", 400);
        LeadEntity lead = leadDao.getLeadByLeadID(leadId);
        if (lead == null) throw new WebApplicationException("Lead não encontrada", 404);

        if (dto.getTitle() != null && !dto.getTitle().isBlank()) lead.setTitulo(dto.getTitle());
        if (dto.getDescription() != null) lead.setDescricao(dto.getDescription());
        if (dto.getState() > 0) lead.setLeadState(dto.getStateEnum());
        lead.setSoftDeleted(dto.isSoftDeleted());

        leadDao.merge(lead);
        auditLogBean.logSystemAction("ADMIN_SUPER_EDIT", "Admin editou manualmente a lead: " + lead.getTitulo());
        return entityToDTO(lead);
    }

    public void hardDeleteLead(Long leadId) {
        LeadEntity lead = leadDao.getLeadByLeadID(leadId);
        if (lead != null) {
            String title = lead.getTitulo();
            leadDao.remove(lead);
            auditLogBean.logSystemAction("HARD_DELETE_LEAD", "Eliminou permanentemente a lead: " + title);
        }
    }

    public int softDeleteAllFromUser(Long userId) {
        if (userDao.find(userId) == null) throw new WebApplicationException("User not found", 404);
        int count = leadDao.bulkUpdateSoftDelete(userId, true);
        auditLogBean.logSystemAction("BULK_SOFT_DELETE", "Moveu todas as leads do user ID " + userId + " para o lixo (" + count + " itens)");
        return count;
    }

    public int undeleteAllFromUser(Long userId) {
        if (userDao.find(userId) == null) throw new WebApplicationException("User not found", 404);
        int count = leadDao.bulkUpdateSoftDelete(userId, false);
        auditLogBean.logSystemAction("BULK_RESTORE", "Restaurou todas as leads do user ID " + userId + " (" + count + " itens)");
        return count;
    }

    public int emptyTrash(Long userId) {
        UserEntity user = userDao.find(userId);
        if (user == null) throw new WebApplicationException("User not found", 404);
        int deletedCount = leadDao.emptyTrashByUserId(userId);
        auditLogBean.logSystemAction("EMPTY_TRASH_LEADS", "Esvaziou lixeira de leads do utilizador: " + user.getUsername());
        return deletedCount;
    }

    public LeadDTO addLeadToUser(Long userId, LeadDTO dto) {
        UserEntity owner = userDao.find(userId);
        if (owner == null) throw new WebApplicationException("User not found", 404);
        if (owner.getState() != UserState.ACTIVE) {
            throw new WebApplicationException("Utilizador inativo", 403);
        }
        LeadEntity newLead = DTOToEntity(dto, owner);
        leadDao.persist(newLead);
        auditLogBean.logSystemAction("ADMIN_CREATE_LEAD", "Atribuiu lead '" + newLead.getTitulo() + "' ao user: " + owner.getUsername());
        return entityToDTO(newLead);
    }
}