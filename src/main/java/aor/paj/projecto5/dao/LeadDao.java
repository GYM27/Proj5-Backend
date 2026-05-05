package aor.paj.projecto5.dao;

import aor.paj.projecto5.entity.LeadEntity;
import aor.paj.projecto5.utils.LeadState;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.ws.rs.WebApplicationException;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * DAO para a entidade Lead.
 * Gere todas as operações de persistência, filtragem e paginação das oportunidades de negócio.
 */
@Stateless
public class LeadDao extends AbstractDao<LeadEntity> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @PersistenceContext(unitName = "project5PU")
    private EntityManager em;

    public LeadDao() {
        super(LeadEntity.class);
    }

    /**
     * Recupera a lista de leads em estado de eliminação lógica (lixeira) para um utilizador específico.
     */
    public List<LeadEntity> getTrashLeadsByUserId(Long userId) {
        return em.createNamedQuery("lead.findSoftDelUserLeads", LeadEntity.class)
                .setParameter("id", userId)
                .getResultList();
    }

    /**
     * Realiza uma pesquisa paginada de leads com suporte a múltiplos filtros dinâmicos.
     * @param stateId ID do estado da lead
     * @param userId ID do proprietário
     * @param softDeleted Estado de eliminação lógica
     * @param search Termo de pesquisa (título ou descrição)
     * @param first Índice do primeiro registo (offset)
     * @param size Número máximo de registos a recuperar
     */
    public List<LeadEntity> findPaginated(Integer stateId, Long userId, Boolean softDeleted, String search, int first, int size) {
        StringBuilder sb = new StringBuilder("SELECT l FROM LeadEntity l WHERE 1=1");
        if (stateId != null) sb.append(" AND l.leadState = :state");
        if (userId != null) sb.append(" AND l.owner.id = :userId");
        if (softDeleted != null) sb.append(" AND l.softDeleted = :softDeleted");
        else sb.append(" AND l.softDeleted = false");
        
        if (search != null && !search.isEmpty()) {
            sb.append(" AND (LOWER(l.titulo) LIKE :search OR LOWER(l.descricao) LIKE :search)");
        }
        sb.append(" ORDER BY l.data DESC");
        
        TypedQuery<LeadEntity> query = em.createQuery(sb.toString(), LeadEntity.class);
        if (stateId != null) query.setParameter("state", LeadState.fromId(stateId));
        if (userId != null) query.setParameter("userId", userId);
        if (softDeleted != null) query.setParameter("softDeleted", softDeleted);
        if (search != null && !search.isEmpty()) query.setParameter("search", "%" + search.toLowerCase() + "%");
        
        return query.setFirstResult(first).setMaxResults(size).getResultList();
    }

    /**
     * Calcula o total de registos que correspondem aos critérios de filtragem.
     * Utilizado para o cálculo de metadados de paginação no frontend.
     */
    public long countPaginated(Integer stateId, Long userId, Boolean softDeleted, String search) {
        StringBuilder sb = new StringBuilder("SELECT COUNT(l) FROM LeadEntity l WHERE 1=1");
        if (stateId != null) sb.append(" AND l.leadState = :state");
        if (userId != null) sb.append(" AND l.owner.id = :userId");
        if (softDeleted != null) sb.append(" AND l.softDeleted = :softDeleted");
        else sb.append(" AND l.softDeleted = false");

        if (search != null && !search.isEmpty()) {
            sb.append(" AND (LOWER(l.titulo) LIKE :search OR LOWER(l.descricao) LIKE :search)");
        }

        TypedQuery<Long> query = em.createQuery(sb.toString(), Long.class);
        if (stateId != null) query.setParameter("state", LeadState.fromId(stateId));
        if (userId != null) query.setParameter("userId", userId);
        if (softDeleted != null) query.setParameter("softDeleted", softDeleted);
        if (search != null && !search.isEmpty()) query.setParameter("search", "%" + search.toLowerCase() + "%");

        return query.getSingleResult();
    }

    /**
     * Método auxiliar para filtragem sem paginação explícita.
     */
    public List<LeadEntity> findLeadsWithFilters(Integer stateId, Long userId, Boolean softDeleted) {
        return findPaginated(stateId, userId, softDeleted, null, 0, Integer.MAX_VALUE);
    }

    /**
     * Executa uma atualização em massa do estado de eliminação lógica para um utilizador.
     * @return O número de registos afetados.
     */
    public int bulkUpdateSoftDelete(Long userId, boolean newStatus) {
        return em.createQuery("UPDATE LeadEntity l SET l.softDeleted = :newStatus " +
                        "WHERE l.owner.id = :userId AND l.softDeleted = :oldStatus")
                .setParameter("newStatus", newStatus)
                .setParameter("oldStatus", !newStatus)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    /**
     * Recupera leads ativas de um utilizador recorrendo a NamedQueries para otimização.
     */
    public List<LeadEntity> getLeadsByUserId(Long userId) {
        return em.createNamedQuery("lead.findLeadsByUserId", LeadEntity.class)
                .setParameter("id", userId)
                .getResultList();
    }

    public LeadEntity getLeadByLeadID(Long leadId) {
        try {
            return em.createNamedQuery("lead.findLeadByLeadID", LeadEntity.class)
                    .setParameter("leadId", leadId)
                    .getSingleResult();
        } catch (Exception e) {
            return null; // O Verifier no Service tratará o 404
        }
    }

    /**
     * Elimina permanentemente da base de dados todas as leads marcadas como 'softDeleted' de um utilizador.
     */
    public int emptyTrashByUserId(Long userId) {
        // JPQL para apagar as leads onde o owner é o utilizador e estão marcadas como softDeleted
        return em.createQuery("DELETE FROM LeadEntity l WHERE l.owner.id = :userId AND l.softDeleted = true")
                .setParameter("userId", userId)
                .executeUpdate();
    }
}