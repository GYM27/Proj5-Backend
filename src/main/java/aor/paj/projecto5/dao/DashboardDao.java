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

        long totalLeads = 0;
        try {
            List<Object[]> leadStats = leadsQuery.getResultList();
            for (Object[] row : leadStats) {
                Object stateObj = row[0];
                Long count = (Long) row[1];
                if (count == null) count = 0L;
                totalLeads += count;

                if (stateObj != null) {
                    LeadState state = null;
                    try {
                        if (stateObj instanceof LeadState) {
                            state = (LeadState) stateObj;
                        } else {
                            // Tenta converter de String para Enum de forma segura
                            String stateStr = stateObj.toString();
                            state = LeadState.valueOf(stateStr.toUpperCase());
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao converter estado da Lead no Dashboard: " + stateObj);
                    }

                    if (state != null) {
                        switch (state) {
                            case NOVO:
                                stats.setNovos(count);
                                break;
                            case ANALISE:
                                stats.setAnalise(count);
                                break;
                            case PROPOSTA:
                                stats.setPropostas(count);
                                break;
                            case GANHO:
                                stats.setGanhos(count);
                                break;
                            case PERDIDO:
                                stats.setPerdidos(count);
                                break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao calcular estados das Leads: " + e.getMessage());
        }
        stats.setLeads(totalLeads);

        // --- CRESCIMENTO DE CLIENTES (Agrupamento Mensal - 1 Ano) ---
        try {
            java.time.LocalDateTime oneYearAgo = java.time.LocalDateTime.now().minusMonths(11).withDayOfMonth(1).withHour(0).withMinute(0);

            // Usamos YEAR/MONTH diretamente, que é mais compatível com Hibernate/JPQL standard
            String growthQueryStr = "SELECT YEAR(c.createdAt), MONTH(c.createdAt), COUNT(c) " +
                    "FROM ClientsEntity c WHERE " + clientCondition + " AND c.createdAt >= :startDate " +
                    "GROUP BY YEAR(c.createdAt), MONTH(c.createdAt) " +
                    "ORDER BY YEAR(c.createdAt) ASC, MONTH(c.createdAt) ASC";

            var growthQuery = em.createQuery(growthQueryStr, Object[].class);
            growthQuery.setParameter("startDate", oneYearAgo);
            if (!role.equals("ADMIN")) growthQuery.setParameter("userId", userId);

            List<Object[]> growthResults = growthQuery.getResultList();
            for (Object[] row : growthResults) {
                int year = ((Number) row[0]).intValue();
                int month = ((Number) row[1]).intValue();
                String date = String.format("%d-%02d", year, month);
                Long count = (Long) row[2];
                stats.getGrowthData().add(new DashboardStatsDTO.GrowthDataDTO(date, count));
            }
        } catch (Exception e) {
            System.err.println("Erro no gráfico de crescimento de clientes: " + e.getMessage());
        }

        // --- CRESCIMENTO DE LEADS (Entrada de Funil - 1 Ano) ---
        try {
            java.time.LocalDateTime oneYearAgo = java.time.LocalDateTime.now().minusMonths(11).withDayOfMonth(1).withHour(0).withMinute(0);
            String leadsGrowthQueryStr = "SELECT YEAR(l.data), MONTH(l.data), COUNT(l) " +
                    "FROM LeadEntity l WHERE " + leadCondition + " AND l.data >= :startDate " +
                    "GROUP BY YEAR(l.data), MONTH(l.data) " +
                    "ORDER BY YEAR(l.data) ASC, MONTH(l.data) ASC";

            var leadsGrowthQuery = em.createQuery(leadsGrowthQueryStr, Object[].class);
            leadsGrowthQuery.setParameter("startDate", oneYearAgo);
            if (!role.equals("ADMIN")) leadsGrowthQuery.setParameter("userId", userId);

            List<Object[]> leadsGrowthResults = leadsGrowthQuery.getResultList();
            for (Object[] row : leadsGrowthResults) {
                int year = ((Number) row[0]).intValue();
                int month = ((Number) row[1]).intValue();
                String date = String.format("%d-%02d", year, month);
                Long count = (Long) row[2];
                stats.getLeadsGrowthData().add(new DashboardStatsDTO.GrowthDataDTO(date, count));
            }
        } catch (Exception e) {
            System.err.println("Erro no gráfico de crescimento de leads: " + e.getMessage());
        }

        // --- ADMIN STATS ---
        if ("ADMIN".equals(role)) {
            try {
                Long totalUsers = em.createQuery("SELECT COUNT(u) FROM UserEntity u WHERE u.state != aor.paj.projecto5.utils.UserState.DISABLED", Long.class).getSingleResult();
                Long confirmedUsers = em.createQuery("SELECT COUNT(u) FROM UserEntity u WHERE u.state = aor.paj.projecto5.utils.UserState.ACTIVE", Long.class).getSingleResult();
                stats.setTotalUsers(totalUsers != null ? totalUsers : 0);
                stats.setConfirmedUsers(confirmedUsers != null ? confirmedUsers : 0);
            } catch (Exception e) {
                System.err.println("Erro ao calcular stats de admin: " + e.getMessage());
            }
        }

        // --- "OS MEUS NÚMEROS" ---
        try {
            Long meusLeads = em.createQuery("SELECT COUNT(l) FROM LeadEntity l WHERE l.softDeleted = false AND l.owner.id = :userId", Long.class)
                    .setParameter("userId", userId)
                    .getSingleResult();

            Long meusClientes = em.createQuery("SELECT COUNT(c) FROM ClientsEntity c WHERE c.softDelete = false AND c.owner.id = :userId", Long.class)
                    .setParameter("userId", userId)
                    .getSingleResult();

            stats.setMeusLeads(meusLeads != null ? meusLeads : 0);
            stats.setMeusClientes(meusClientes != null ? meusClientes : 0);
        } catch (Exception e) {
            System.err.println("Erro ao calcular performance pessoal: " + e.getMessage());
        }

        return stats;
    }
}