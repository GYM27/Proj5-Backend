package aor.paj.projecto5.bean;

import aor.paj.projecto5.dao.DashboardDao;
import aor.paj.projecto5.dto.DashboardStatsDTO;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * O meu "cérebro" das estatísticas. 
 * Ele é muito simples porque a inteligência está toda no DashboardDao, 
 * mas serve de ponte para o Service.
 */
@Stateless
public class DashboardBean {

    @Inject
    private DashboardDao dashboardDao;

    /**
     * Pede ao DAO os números para o Dashboard. 
     * Passo o ID do utilizador e o seu papel (role) para ele saber o que contar.
     */
    public DashboardStatsDTO getStats(Long userId, String role) {
        // A regra de negócio delega a responsabilidade de acesso a dados para o DAO
        return dashboardDao.getDashboardStats(userId, role);
    }
}
