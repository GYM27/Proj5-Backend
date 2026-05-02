package aor.paj.projecto5.dao;

import aor.paj.projecto5.dto.DashboardStatsDTO;
import aor.paj.projecto5.utils.LeadState;
import aor.paj.projecto5.utils.UserState;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 * Motor de estatísticas para o Dashboard (Versão Corrigida para Histórico de Equipa).
 */
@Stateless
public class DashboardDao implements Serializable {

    private static final long serialVersionUID = 1L;

    @PersistenceContext(unitName = "project5PU")
    private EntityManager em;

    public DashboardStatsDTO getDashboardStats(Long userId, String role, Long targetUserId) {
        DashboardStatsDTO stats = new DashboardStatsDTO();

        // Determinar se estamos a filtrar por um utilizador específico (Apenas para Admins)
        boolean isFiltering = "ADMIN".equals(role) && targetUserId != null;
        Long effectiveUserId = isFiltering ? targetUserId : userId;
        String effectiveRole = isFiltering ? "VENDEDOR" : role;

        // 1. Condições de Visibilidade (Leads e Clientes)
        String leadCondition = effectiveRole.equals("ADMIN") ? "l.softDeleted = false" : "l.softDeleted = false AND l.owner.id = :userId";
        String clientCondition = effectiveRole.equals("ADMIN") ? "c.softDelete = false" : "c.softDelete = false AND c.owner.id = :userId";

        // --- CONTAGENS BÁSICAS ---
        try {
            var clientsQuery = em.createQuery("SELECT COUNT(c) FROM ClientsEntity c WHERE " + clientCondition, Long.class);
            if (!effectiveRole.equals("ADMIN")) clientsQuery.setParameter("userId", effectiveUserId);
            stats.setClientes(clientsQuery.getSingleResult());

            var leadsQuery = em.createQuery("SELECT l.leadState, COUNT(l) FROM LeadEntity l WHERE " + leadCondition + " GROUP BY l.leadState", Object[].class);
            if (!effectiveRole.equals("ADMIN")) leadsQuery.setParameter("userId", effectiveUserId);
            long totalLeads = 0;
            for (Object[] row : (List<Object[]>) leadsQuery.getResultList()) {
                LeadState state = (LeadState) row[0];
                long count = (Long) row[1];
                totalLeads += count;
                switch (state) {
                    case NOVO: stats.setNovos(count); break;
                    case ANALISE: stats.setAnalise(count); break;
                    case PROPOSTA: stats.setPropostas(count); break;
                    case GANHO: stats.setGanhos(count); break;
                    case PERDIDO: stats.setPerdidos(count); break;
                }
            }
            stats.setLeads(totalLeads);
        } catch (Exception e) {}

        // --- CRESCIMENTO TEMPORAL (12 Meses) ---
        java.time.LocalDateTime oneYearAgo = java.time.LocalDateTime.now().minusMonths(11).withDayOfMonth(1).withHour(0).withMinute(0);
        
        try {
            // Clientes
            var qC = em.createQuery("SELECT YEAR(c.createdAt), MONTH(c.createdAt), COUNT(c) FROM ClientsEntity c WHERE " + clientCondition + " AND c.createdAt >= :startDate GROUP BY YEAR(c.createdAt), MONTH(c.createdAt) ORDER BY 1, 2", Object[].class).setParameter("startDate", oneYearAgo);
            if (!effectiveRole.equals("ADMIN")) qC.setParameter("userId", effectiveUserId);
            for (Object[] r : qC.getResultList()) stats.getGrowthData().add(new DashboardStatsDTO.GrowthDataDTO(String.format("%d-%02d", ((Number)r[0]).intValue(), ((Number)r[1]).intValue()), (Long)r[2]));

            // Leads
            var qL = em.createQuery("SELECT YEAR(l.data), MONTH(l.data), COUNT(l) FROM LeadEntity l WHERE " + leadCondition + " AND l.data >= :startDate GROUP BY YEAR(l.data), MONTH(l.data) ORDER BY 1, 2", Object[].class).setParameter("startDate", oneYearAgo);
            if (!effectiveRole.equals("ADMIN")) qL.setParameter("userId", effectiveUserId);
            for (Object[] r : qL.getResultList()) stats.getLeadsGrowthData().add(new DashboardStatsDTO.GrowthDataDTO(String.format("%d-%02d", ((Number)r[0]).intValue(), ((Number)r[1]).intValue()), (Long)r[2]));
        } catch (Exception e) {}

        // --- 4. ESTATÍSTICAS DE EQUIPA (AQUI ESTÁ A CORREÇÃO CRÍTICA) ---
        if ("ADMIN".equals(role)) {
            try {
                // Crescimento de Users (Todos no histórico)
                var qU = em.createQuery("SELECT YEAR(u.createdAt), MONTH(u.createdAt), COUNT(u) FROM UserEntity u WHERE u.createdAt >= :startDate AND u.username != 'deleted_user' GROUP BY YEAR(u.createdAt), MONTH(u.createdAt) ORDER BY 1, 2", Object[].class).setParameter("startDate", oneYearAgo);
                for (Object[] r : qU.getResultList()) stats.getUsersGrowthData().add(new DashboardStatsDTO.GrowthDataDTO(String.format("%d-%02d", ((Number)r[0]).intValue(), ((Number)r[1]).intValue()), (Long)r[2]));

                // A) TOTAL HISTÓRICO: Contamos TODOS os utilizadores na tabela 'users' (exceto o sistema)
                Long total = em.createQuery("SELECT COUNT(u) FROM UserEntity u WHERE u.username != 'deleted_user'", Long.class).getSingleResult();
                stats.setTotalUsers(total);

                // B) ATIVOS: Apenas quem está mesmo ACTIVE
                Long ativos = em.createQuery("SELECT COUNT(u) FROM UserEntity u WHERE u.state = aor.paj.projecto5.utils.UserState.ACTIVE", Long.class).getSingleResult();
                stats.setConfirmedUsers(ativos);

                // C) INATIVOS: Apenas quem está mesmo DISABLED
                Long inativos = em.createQuery("SELECT COUNT(u) FROM UserEntity u WHERE u.state = aor.paj.projecto5.utils.UserState.DISABLED", Long.class).getSingleResult();
                stats.setInactiveUsers(inativos);

                // Debug para a consola do Wildfly (Podes verificar aqui se os números batem certo)
                System.out.println(">>> DASHBOARD EQUIPA: Total=" + total + " | Ativos=" + ativos + " | Inativos=" + inativos);

                // D) TOP PERFORMERS (Melhor Taxa de Conversão)
                // Apenas se for uma visão global (sem targetUserId)
                if (targetUserId == null) {
                    String jpql = "SELECT u.username, " +
                                  "COUNT(l.id), " +
                                  "SUM(CASE WHEN l.leadState = aor.paj.projecto5.utils.LeadState.GANHO THEN 1 ELSE 0 END) " +
                                  "FROM LeadEntity l " +
                                  "JOIN l.owner u " +
                                  "WHERE l.softDeleted = false AND u.state = aor.paj.projecto5.utils.UserState.ACTIVE " +
                                  "GROUP BY u.username " +
                                  "HAVING COUNT(l.id) > 0";
                                  
                    var query = em.createQuery(jpql, Object[].class);
                    List<Object[]> results = query.getResultList();
                    List<DashboardStatsDTO.UserPerformanceDTO> topPerformers = new ArrayList<>();
                    
                    for (Object[] row : results) {
                        String username = (String) row[0];
                        long totalL = ((Number) row[1]).longValue();
                        long wonL = ((Number) row[2]).longValue();
                        double convRate = (double) wonL / totalL * 100.0;
                        topPerformers.add(new DashboardStatsDTO.UserPerformanceDTO(username, totalL, wonL, convRate));
                    }
                    
                    // Ordenar por taxa de conversão DESC
                    topPerformers.sort((a, b) -> Double.compare(b.getConversionRate(), a.getConversionRate()));
                    
                    // Limitar a 10
                    if (topPerformers.size() > 10) {
                        topPerformers = topPerformers.subList(0, 10);
                    }
                    stats.setTopPerformers(topPerformers);
                }

            } catch (Exception e) {
                System.err.println("Erro ao calcular equipa: " + e.getMessage());
            }
        }

        // --- 5. PERFORMANCE PESSOAL ---
        try {
            stats.setMeusLeads(em.createQuery("SELECT COUNT(l) FROM LeadEntity l WHERE l.softDeleted = false AND l.owner.id = :id", Long.class).setParameter("id", effectiveUserId).getSingleResult());
            stats.setMeusClientes(em.createQuery("SELECT COUNT(c) FROM ClientsEntity c WHERE c.softDelete = false AND c.owner.id = :id", Long.class).setParameter("id", effectiveUserId).getSingleResult());
        } catch (Exception e) {}

        return stats;
    }
}