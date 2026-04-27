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
 * Uso este Bean para enviar emails (convites, recuperação de pass, etc). 
 * Está configurado para usar o MailHog localmente, o que facilita imenso os testes.
 */
@Stateless
public class EmailBean {

    private static final Logger logger = LogManager.getLogger(EmailBean.class);

    // TODO: O ideal é mover estas credenciais para variáveis de ambiente ou para o standalone.xml do Wildfly
    // Para um projeto académico, podes usar um SMTP de teste gratuito como o Mailtrap.io ou o teu próprio Gmail com App Password
    // Configuração para MAILHOG (Ambiente de Testes Local)
    private static final String SMTP_HOST = "localhost"; 
    private static final String SMTP_PORT = "1025";
    private static final String SENDER_EMAIL = "noreply@crmbridge.com";

    /**
     * O truque da performance: @Asynchronous! 
     * Usei esta anotação para que o envio do email aconteça "nas costas" do utilizador. 
     * Assim, o React recebe o "OK" instantaneamente e não tem de esperar que o servidor 
     * SMTP responda.
     */
    @Asynchronous
    public void sendEmail(String toEmail, String subject, String body) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "false"); // MailHog não exige autenticação
            props.put("mail.smtp.starttls.enable", "false"); 
            props.put("mail.smtp.host", SMTP_HOST);
            props.put("mail.smtp.port", SMTP_PORT);

            Session session = Session.getInstance(props);

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setContent(body, "text/html; charset=utf-8");

            Transport.send(message);
            logger.info("Email enviado com sucesso para: " + toEmail);

        } catch (MessagingException e) {
            logger.error("Falha ao enviar e-mail para: " + toEmail, e);
        }
    }
}
