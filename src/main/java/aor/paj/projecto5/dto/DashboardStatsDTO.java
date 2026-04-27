package aor.paj.projecto5.dto;

import java.io.Serializable;

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
}
