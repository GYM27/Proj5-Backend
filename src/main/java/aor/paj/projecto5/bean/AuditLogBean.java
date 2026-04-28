package aor.paj.projecto5.bean;

import aor.paj.projecto5.dao.AuditLogDao;
import aor.paj.projecto5.entity.AuditLogEntity;
import aor.paj.projecto5.entity.UserEntity;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bean responsável por centralizar o registo de auditoria.
 * Agora escreve tanto na base de dados como num ficheiro de texto via Log4j2.
 */
@Stateless
public class AuditLogBean implements Serializable {

    private static final long serialVersionUID = 1L;
    
    // Logger específico para auditoria
    private static final Logger auditLogger = LogManager.getLogger(AuditLogBean.class);

    @Inject
    private AuditLogDao auditLogDao;

    /**
     * Regista uma nova atividade.
     */
    @Asynchronous
    public void logAction(UserEntity user, String action, String details) {
        // 1. Registo em base de dados (Backup estruturado)
        AuditLogEntity log = new AuditLogEntity(user, action, details);
        auditLogDao.persist(log);

        // 2. Registo em ficheiro de texto (Legível para humanos)
        String userName = (user != null) ? user.getUsername() : "SYSTEM";
        auditLogger.info(String.format("%-15s | %-20s | %s", userName, action, details));
    }
    
    @Asynchronous
    public void logSystemAction(String action, String details) {
        logAction(null, action, details);
    }
}
