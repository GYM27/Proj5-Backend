package aor.paj.projecto5.bean;

import jakarta.ejb.Asynchronous;
import jakarta.ejb.Stateless;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

/**
 * O meu estafeta digital! 
 * Configurado para MailHog (localhost:1025).
 */
@Stateless
public class EmailBean {

    private static final Logger logger = LogManager.getLogger(EmailBean.class);

    private static final String SMTP_HOST = "localhost"; 
    private static final String SMTP_PORT = "1025";
    private static final String SENDER_EMAIL = "noreply@crmbridge.com";

    /**
     * Envia o email de forma assíncrona para não bloquear o utilizador.
     */
    @Asynchronous
    public void sendEmail(String toEmail, String subject, String body) {
        logger.info("A iniciar processo de envio de email para: " + toEmail);
        
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "false");
            props.put("mail.smtp.starttls.enable", "false"); 
            props.put("mail.smtp.host", SMTP_HOST);
            props.put("mail.smtp.port", SMTP_PORT);
            
            // Timeout curto para não prender threads se o MailHog estiver desligado
            props.put("mail.smtp.connectiontimeout", "3000"); 
            props.put("mail.smtp.timeout", "3000");

            Session session = Session.getInstance(props);

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setContent(body, "text/html; charset=utf-8");

            // Tenta o envio. Se o MailHog estiver desligado, o erro é apanhado no catch
            Transport.send(message);
            
            logger.info("Email enviado com sucesso (via MailHog) para: " + toEmail);

        } catch (Exception e) {
            // SILENCIAMOS o erro para o Frontend não receber um 500
            // O email falha silenciosamente no servidor (apenas log), mas o processo do utilizador continua.
            logger.error("ERRO CRÍTICO NO ENVIO DE EMAIL: O MailHog está ligado? Erro: " + e.getMessage());
        }
    }
}
