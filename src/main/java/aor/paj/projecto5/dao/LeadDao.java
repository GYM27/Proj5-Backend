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
 * O motor de busca das minhas Leads. 
 * Aqui é onde o JPA brilha, convertendo as minhas queries em SQL para o PostgreSQL.
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
     * Vai buscar APENAS as leads que estão na lixeira de um utilizador normal
     */
    public List<LeadEntity> getTrashLeadsByUserId(Long userId) {
        return em.createNamedQuery("lead.findSoftDelUserLeads", LeadEntity.class)
                .setParameter("id", userId)
                .getResultList();
    }

    /**
     * O meu "Super Filtro". 
     * Como o Admin precisa de filtrar por tudo e mais alguma coisa, criei esta query dinâmica 
     * usando um StringBuilder para ir acrescentando as condições conforme o que o React manda.
     */
    public List<LeadEntity> findLeadsWithFilters(Integer stateId, Long userId, Boolean softDeleted) {
        StringBuilder sb = new StringBuilder("SELECT l FROM LeadEntity l WHERE 1=1");

        // Construção dinâmica da query (Append)
        if (stateId != null) sb.append(" AND l.leadState = :state");
        if (userId != null) sb.append(" AND l.owner.id = :userId");

        if (softDeleted != null) {
            sb.append(" AND l.softDeleted = :softDeleted");
        } else {
            sb.append(" AND l.softDeleted = false"); // Padrão: não mostrar lixo
        }

        TypedQuery<LeadEntity> query = em.createQuery(sb.toString(), LeadEntity.class);

        // Atribuição segura de parâmetros (Set)
        if (stateId != null) {
            try {
                query.setParameter("state", LeadState.fromId(stateId));
            } catch (Exception e) {
                throw new WebApplicationException("Estado de lead inválido: " + stateId, 400);
            }
        }
        if (userId != null) query.setParameter("userId", userId);
        if (softDeleted != null) query.setParameter("softDeleted", softDeleted);

        return query.getResultList();
    }

    /**
     * Alterar o estado de várias leads ao mesmo tempo. 
     * É o que chamo de "Update Atómico" - uma só viagem à base de dados para mudar tudo.
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
     * 3. NAMED QUERIES (Performance)
     * Usa as definições que já tens na LeadEntity para casos fixos.
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
     * A limpeza final. 
     * Vou à tabela de leads e apago permanentemente tudo o que este user tinha na lixeira.
     */
    public int emptyTrashByUserId(Long userId) {
        // JPQL para apagar as leads onde o owner é o utilizador e estão marcadas como softDeleted
        return em.createQuery("DELETE FROM LeadEntity l WHERE l.owner.id = :userId AND l.softDeleted = true")
                .setParameter("userId", userId)
                .executeUpdate();
    }
}