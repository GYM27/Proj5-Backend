package aor.paj.projecto5.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DashboardStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long novos;
    private long analise;
    private long propostas;
    private long ganhos;
    private long perdidos;
    private long leads;
    private long clientes;
    private long totalUsers;
    private long confirmedUsers;
    private long meusLeads;
    private long meusClientes;
    private List<GrowthDataDTO> growthData = new ArrayList<>();
    private List<GrowthDataDTO> leadsGrowthData = new ArrayList<>();

    // DTO para o gráfico de crescimento (ISO8601)
    public static class GrowthDataDTO implements Serializable {
        private String date; // YYYY-MM-DD
        private long count;
        public GrowthDataDTO(String date, long count) { this.date = date; this.count = count; }
        public String getDate() { return date; }
        public long getCount() { return count; }
    }

    public DashboardStatsDTO() {
    }

    public long getNovos() { return novos; }
    public void setNovos(long novos) { this.novos = novos; }

    public long getAnalise() { return analise; }
    public void setAnalise(long analise) { this.analise = analise; }

    public long getPropostas() { return propostas; }
    public void setPropostas(long propostas) { this.propostas = propostas; }

    public long getGanhos() { return ganhos; }
    public void setGanhos(long ganhos) { this.ganhos = ganhos; }

    public long getPerdidos() { return perdidos; }
    public void setPerdidos(long perdidos) { this.perdidos = perdidos; }

    public long getLeads() { return leads; }
    public void setLeads(long leads) { this.leads = leads; }

    public long getClientes() { return clientes; }
    public void setClientes(long clientes) { this.clientes = clientes; }

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }

    public long getConfirmedUsers() { return confirmedUsers; }
    public void setConfirmedUsers(long confirmedUsers) { this.confirmedUsers = confirmedUsers; }

    public List<GrowthDataDTO> getGrowthData() { return growthData; }
    public void setGrowthData(List<GrowthDataDTO> growthData) { this.growthData = growthData; }

    public List<GrowthDataDTO> getLeadsGrowthData() { return leadsGrowthData; }
    public void setLeadsGrowthData(List<GrowthDataDTO> leadsGrowthData) { this.leadsGrowthData = leadsGrowthData; }

    public long getMeusLeads() { return meusLeads; }
    public void setMeusLeads(long meusLeads) { this.meusLeads = meusLeads; }

    public long getMeusClientes() { return meusClientes; }
    public void setMeusClientes(long meusClientes) { this.meusClientes = meusClientes; }
}
