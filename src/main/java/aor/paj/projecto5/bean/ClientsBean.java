package aor.paj.projecto5.bean;

import java.util.List;
import java.util.stream.Collectors;
import aor.paj.projecto5.dao.ClientsDao;
import aor.paj.projecto5.dao.UserDao;
import aor.paj.projecto5.dto.ClientsDTO;
import aor.paj.projecto5.entity.ClientsEntity;
import aor.paj.projecto5.entity.UserEntity;
import aor.paj.projecto5.utils.UserRoles;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * EJB para gestão de Clientes com Auditoria integrada.
 */
@Stateless
public class ClientsBean {

    private static final Logger logger = LogManager.getLogger(ClientsBean.class);

    @Inject
    TokenBean tokenBean;

    @Inject
    ClientsDao clientsDao;

    @Inject
    UserDao userDao;

    @Inject
    AuditLogBean auditLogBean;

    public ClientsBean() {}

    // --- Helpers ---

    private ClientsDTO toDTO(ClientsEntity entity) {
        ClientsDTO dto = new ClientsDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setOrganization(entity.getOrganization());
        dto.setSoftDeleted(entity.isSoftDelete());

        if (entity.getOwner() != null) {
            String firstName = entity.getOwner().getFirstName();
            String lastName = entity.getOwner().getLastName();
            String username = entity.getOwner().getUsername();

            if ((firstName == null || firstName.isEmpty()) && (lastName == null || lastName.isEmpty())) {
                dto.setOwnerName(username); // Fallback para o username
            } else {
                String fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
                dto.setOwnerName(fullName.trim());
            }
        }
        return dto;
    }

    private ClientsEntity toEntity(ClientsDTO dto, UserEntity owner) {
        ClientsEntity entity = new ClientsEntity();
        entity.setName(dto.getName());
        entity.setEmail(dto.getEmail());
        entity.setPhone(dto.getPhone());
        entity.setOrganization(dto.getOrganization());
        entity.setOwner(owner);
        entity.setSoftDelete(dto.isSoftDeleted());
        return entity;
    }

    private List<ClientsDTO> toDTOList(List<ClientsEntity> entities) {
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // --- CRUD ---

    public ClientsDTO addClient(String token, ClientsDTO dto) {
        UserEntity owner = tokenBean.getUserEntityByToken(token);
        ClientsEntity newClient = toEntity(dto, owner);
        clientsDao.persist(newClient);
        
        auditLogBean.logAction(owner, "CREATE_CLIENT", "Adicionou o cliente: " + newClient.getName());
        return toDTO(newClient);
    }

    public ClientsDTO editClient(String token, Long clientId, ClientsDTO dto) {
        UserEntity editor = tokenBean.getUserEntityByToken(token);
        ClientsEntity client = clientsDao.find(clientId);

        if (client == null || client.isSoftDelete()) {
            throw new WebApplicationException("Cliente não encontrado", Response.Status.NOT_FOUND);
        }

        client.setName(dto.getName());
        client.setEmail(dto.getEmail());
        client.setPhone(dto.getPhone());
        client.setOrganization(dto.getOrganization());
        clientsDao.merge(client);

        auditLogBean.logAction(editor, "UPDATE_CLIENT", "Editou o cliente: " + client.getName());
        return toDTO(client);
    }

    public ClientsDTO softDeleteClient(String token, Long clientId) {
        UserEntity user = tokenBean.getUserEntityByToken(token);
        ClientsEntity client = clientsDao.find(clientId);
        if (client == null || client.isSoftDelete()) {
            throw new WebApplicationException("Cliente não encontrado", Response.Status.NOT_FOUND);
        }
        client.setSoftDelete(true);
        clientsDao.merge(client);
        
        auditLogBean.logAction(user, "DELETE_CLIENT", "Moveu para a lixeira o cliente: " + client.getName());
        return toDTO(client);
    }

    public ClientsDTO restoreClient(String token, Long clientId) {
        UserEntity user = tokenBean.getUserEntityByToken(token);
        ClientsEntity client = clientsDao.find(clientId);
        if (client == null || !client.isSoftDelete()) {
            throw new WebApplicationException("Cliente não encontrado", Response.Status.NOT_FOUND);
        }
        client.setSoftDelete(false);
        clientsDao.merge(client);
        
        auditLogBean.logAction(user, "RESTORE_CLIENT", "Restaurou o cliente: " + client.getName());
        return toDTO(client);
    }

    public ClientsDTO permanentDeleteClient(String token, Long clientId) {
        UserEntity user = tokenBean.getUserEntityByToken(token);
        ClientsEntity client = clientsDao.find(clientId);
        if (client == null || !client.isSoftDelete()) {
            throw new WebApplicationException("O cliente deve estar na lixeira para remoção", Response.Status.CONFLICT);
        }
        String clientName = client.getName();
        ClientsDTO dto = toDTO(client);
        clientsDao.remove(client);
        
        auditLogBean.logAction(user, "HARD_DELETE_CLIENT", "Eliminou permanentemente o cliente: " + clientName);
        return dto;
    }

    // --- Listagem e Bulk ---

    public List<ClientsDTO> listClients(String token, Long userId) {
        UserEntity requester = tokenBean.getUserEntityByToken(token);
        if (requester == null) throw new WebApplicationException(401);
        Long filterId = (requester.getUserRole() == UserRoles.ADMIN) ? userId : requester.getId();
        return toDTOList(clientsDao.findClientsWithFilters(filterId, false));
    }

    public List<ClientsDTO> listDeletedClientsDTO(String token, Long userId) {
        UserEntity requester = tokenBean.getUserEntityByToken(token);
        if (requester == null) throw new WebApplicationException(401);
        Long filterId = (requester.getUserRole() == UserRoles.ADMIN) ? userId : requester.getId();
        return toDTOList(clientsDao.findClientsWithFilters(filterId, true));
    }

    public int softDeleteAllClientsByUser(Long userId) {
        if (userDao.find(userId) == null) throw new WebApplicationException("User not found", 404);
        return clientsDao.bulkUpdateSoftDelete(userId, true);
    }

    public int unSoftDeleteAllClientsByUser(Long userId) {
        if (userDao.find(userId) == null) throw new WebApplicationException("User not found", 404);
        return clientsDao.bulkUpdateSoftDelete(userId, false);
    }

    public boolean emptyTrash(Long userId) {
        try {
            List<ClientsEntity> trashList = clientsDao.findClientsWithFilters(userId, true);
            for (ClientsEntity c : trashList) {
                clientsDao.remove(c);
            }
            auditLogBean.logSystemAction("EMPTY_TRASH_CLIENTS", "Lixeira de clientes esvaziada para o user ID: " + userId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public ClientsDTO createClientForUser(Long userId, ClientsDTO dto) {
        UserEntity targetOwner = userDao.find(userId);
        if (targetOwner == null) throw new WebApplicationException("User not found", 404);
        ClientsEntity newClient = toEntity(dto, targetOwner);
        clientsDao.persist(newClient);
        auditLogBean.logSystemAction("ADMIN_CREATE_CLIENT", "Admin atribuiu cliente '" + newClient.getName() + "' ao user: " + targetOwner.getUsername());
        return toDTO(newClient);
    }

    public List<ClientsDTO> listAllDeletedClients() {
        return toDTOList(clientsDao.findClientsWithFilters(null, true));
    }
}