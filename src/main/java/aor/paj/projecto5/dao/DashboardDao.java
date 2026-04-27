package aor.paj.projecto5.dao;

import aor.paj.projecto5.dto.DashboardStatsDTO;
import aor.paj.projecto5.utils.LeadState;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.Serializable;
import java.util.List;

/**
 * O meu motor de estatísticas. 
 * É aqui que faço as contas pesadas para o Dashboard. 
 * O desafio aqui foi garantir que um Admin vê os números globais da empresa, 
 * mas um Vendedor vê apenas o seu próprio progresso.
 */
@Stateless
public class DashboardDao implements Serializable {

    private static final long serialVersionUID = 1L;

    @PersistenceContext(unitName = "project5PU")
    private EntityManager em;

    /**
     * Calcula todos os números de uma só vez para evitar múltiplas chamadas à BD.
     * Uso condições dinâmicas (Strings) para filtrar por ID de utilizador se não for Admin.
     */
    public DashboardStatsDTO getDashboardStats(Long userId, String role) {
        DashboardStatsDTO stats = new DashboardStatsDTO();

        // Condições dinâmicas para Administrador vs Utilizador Comum
        String leadCondition = role.equals("ADMIN") ? "l.softDeleted = false" : "l.softDeleted = false AND l.owner.id = :userId";
        String clientCondition = role.equals("ADMIN") ? "c.softDelete = false" : "c.softDelete = false AND c.owner.id = :userId";

        // Total Clientes
        var clientsQuery = em.createQuery("SELECT COUNT(c) FROM ClientsEntity c WHERE " + clientCondition, Long.class);
        if (!role.equals("ADMIN")) {
            clientsQuery.setParameter("userId", userId);
        }
        Long totalClients = clientsQuery.getSingleResult();
        stats.setClientes(totalClients != null ? totalClients : 0);

        // Agregação de Leads por Estado: 
        // Em vez de fazer 5 queries para cada estado, faço uma que me traz logo o grupo todo.
        var leadsQuery = em.createQuery(
                "SELECT l.leadState, COUNT(l) FROM LeadEntity l WHERE " + leadCondition + " GROUP BY l.leadState", Object[].class);
        if (!role.equals("ADMIN")) {
            leadsQuery.setParameter("userId", userId);
        }
        
        List<Object[]> leadStats = leadsQuery.getResultList();

        long totalLeads = 0;
        for (Object[] row : leadStats) {
            LeadState state = (LeadState) row[0];
            Long count = (Long) row[1];
            totalLeads += count;

            if (state != null) {
                switch (state) {
                    case NOVO: stats.setNovos(count); break;
                    case ANALISE: stats.setAnalise(count); break;
                    case PROPOSTA: stats.setPropostas(count); break;
                    case GANHO: stats.setGanhos(count); break;
                    case PERDIDO: stats.setPerdidos(count); break;
                }
            }
        }
        stats.setLeads(totalLeads);

        // Se for ADMIN, calcula também estatísticas de Utilizadores
        if ("ADMIN".equals(role)) {
            Long totalUsers = em.createQuery("SELECT COUNT(u) FROM UserEntity u WHERE u.state != aor.paj.projecto5.utils.UserState.DISABLED", Long.class).getSingleResult();
            Long confirmedUsers = em.createQuery("SELECT COUNT(u) FROM UserEntity u WHERE u.state = aor.paj.projecto5.utils.UserState.ACTIVE", Long.class).getSingleResult();
            
            stats.setTotalUsers(totalUsers != null ? totalUsers : 0);
            stats.setConfirmedUsers(confirmedUsers != null ? confirmedUsers : 0);
        }

        return stats;
    }
}
