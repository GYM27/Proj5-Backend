package aor.paj.projecto5.dto;

/**
 * Objeto de transferência de dados para mensagens.
 * Uso esta classe para capturar o que o utilizador escreve no frontend
 * e transportar esses dados de forma segura até ao meu serviço REST.
 * Adiciono o campo timestamp para registar o momento exato do envio.
 * * Isto ajuda-me a ordenar as mensagens no ecrã do destinatário.
 */
public class MessageDto {

    private String sender;   // Username do remetente
    private String receiver; // Username do destino
    private String content;  // O texto da mensagem
    private long timestamp;  // hora da mensagem
    private String type;     // Tipo da mensagem (ex: "CHAT")
    private boolean read;    // Estado de leitura

    // Construtor vazio necessário para o Jackson (JSON)
    public MessageDto() {
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    /**
     * Getters e Setters para que o servidor consiga ler e escrever os dados.
     */
    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}