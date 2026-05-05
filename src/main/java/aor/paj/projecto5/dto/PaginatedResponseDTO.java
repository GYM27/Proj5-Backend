package aor.paj.projecto5.dto;

import java.util.List;

/**
 * DTO genérico para encapsulamento de respostas paginadas.
 * Transporta a lista de registos juntamente com os metadados necessários para a navegação no frontend.
 * @param <T> Tipo da entidade ou DTO contido na resposta.
 */
public class PaginatedResponseDTO<T> {
    private List<T> items;
    private long totalItems;
    private int totalPages;
    private int currentPage;

    public PaginatedResponseDTO() {}

    public PaginatedResponseDTO(List<T> items, long totalItems, int page, int size) {
        this.items = items;
        this.totalItems = totalItems;
        this.currentPage = page;
        this.totalPages = (int) Math.ceil((double) totalItems / size);
    }

    // Getters e Setters
    public List<T> getItems() { return items; }
    public void setItems(List<T> items) { this.items = items; }

    public long getTotalItems() { return totalItems; }
    public void setTotalItems(long totalItems) { this.totalItems = totalItems; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
}
