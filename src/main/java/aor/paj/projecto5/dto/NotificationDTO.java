package aor.paj.projecto5.dto;

import java.sql.Timestamp;

public class NotificationDTO {
    private long id;
    private String content;
    private String type;
    private boolean isRead;
    private Timestamp creatAt;

    public NotificationDTO() {}

    // Getters e Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public Timestamp getCreatAt() { return creatAt; }
    public void setCreatAt(Timestamp creatAt) { this.creatAt = creatAt; }
}
