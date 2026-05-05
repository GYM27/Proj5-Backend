package aor.paj.projecto5.dao;

import java.util.List;

import aor.paj.projecto5.entity.ClientsEntity;
import aor.paj.projecto5.entity.UserEntity;
import jakarta.ejb.Stateless;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

/**
 * O meu gestor de persistência para Clientes. 
 * É aqui que defino as regras de negócio ao nível da Base de Dados, como os filtros 
 * de pesquisa e a validação de emails duplicados.
 */
@Stateless
public class ClientsDao extends AbstractDao<ClientsEntity> {

    public ClientsDao() {
        super(ClientsEntity.class);
    }

    /**
     * Procura clientes com paginação e filtro de pesquisa (Requisito Backend).
     */
    public List<ClientsEntity> findPaginated(Long ownerId, Boolean softDelete, String search, int first, int size) {
        StringBuilder sb = new StringBuilder("SELECT c FROM ClientsEntity c WHERE 1=1");
        if (ownerId != null) sb.append(" AND c.owner.id = :ownerId");
        if (softDelete != null) sb.append(" AND c.softDelete = :softDelete");
        if (search != null && !search.isEmpty()) {
            sb.append(" AND (LOWER(c.name) LIKE :search OR LOWER(c.email) LIKE :search OR LOWER(c.phone) LIKE :search OR LOWER(c.organization) LIKE :search)");
        }
        sb.append(" ORDER BY c.id DESC");
        
        TypedQuery<ClientsEntity> query = em.createQuery(sb.toString(), ClientsEntity.class);
        if (ownerId != null) query.setParameter("ownerId", ownerId);
        if (softDelete != null) query.setParameter("softDelete", softDelete);
        if (search != null && !search.isEmpty()) query.setParameter("search", "%" + search.toLowerCase() + "%");
        
        return query.setFirstResult(first).setMaxResults(size).getResultList();
    }

    /**
     * Conta o total de clientes para a paginação.
     */
    public long countPaginated(Long ownerId, Boolean softDelete, String search) {
        StringBuilder sb = new StringBuilder("SELECT COUNT(c) FROM ClientsEntity c WHERE 1=1");
        if (ownerId != null) sb.append(" AND c.owner.id = :ownerId");
        if (softDelete != null) sb.append(" AND c.softDelete = :softDelete");
        if (search != null && !search.isEmpty()) {
            sb.append(" AND (LOWER(c.name) LIKE :search OR LOWER(c.email) LIKE :search OR LOWER(c.phone) LIKE :search OR LOWER(c.organization) LIKE :search)");
        }
        
        TypedQuery<Long> query = em.createQuery(sb.toString(), Long.class);
        if (ownerId != null) query.setParameter("ownerId", ownerId);
        if (softDelete != null) query.setParameter("softDelete", softDelete);
        if (search != null && !search.isEmpty()) query.setParameter("search", "%" + search.toLowerCase() + "%");
        
        return query.getSingleResult();
    }

    /**
     * O meu filtro dinâmico de clientes. 
     */
    public List<ClientsEntity> findClientsWithFilters(Long ownerId, Boolean softDelete) {
        return findPaginated(ownerId, softDelete, null, 0, Integer.MAX_VALUE);
    }

    /**
     * Atualização em massa! 
     * Útil quando quero desativar todos os clientes de um vendedor que saiu da empresa.
     */
    public int bulkUpdateSoftDelete(Long userId, boolean newStatus) {
      return  em.createQuery("UPDATE ClientsEntity c SET c.softDelete = :newStatus WHERE c.owner.id = :userId")
                .setParameter("newStatus", newStatus)
                .setParameter("userId", userId)
                .executeUpdate();


    }

    public void hardDelete(Long id) {
        ClientsEntity client = em.find(ClientsEntity.class, id);
        if (client != null) {
            em.remove(client);
        }
    }

    /**
     * Uma proteção extra: não deixo o mesmo vendedor registar dois clientes com o mesmo email.
     */
    public boolean isEmailDuplicated(String email, UserEntity owner) {
        try {
            Long count = em.createQuery(
                            "SELECT COUNT(c) FROM ClientsEntity c WHERE c.email = :email AND c.owner = :owner", Long.class)
                    .setParameter("email", email)
                    .setParameter("owner", owner)
                    .getSingleResult();
            return count > 0;
        } catch (NoResultException e) {
            return false;
        }
    }
}