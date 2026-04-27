package aor.paj.projecto5.websocket;

import aor.paj.projecto5.bean.TokenBean;
import aor.paj.projecto5.entity.UserEntity;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;

/**
 * Gestor central de comunicações WebSocket para o sistema de chat e notificações.
 * Esta classe funciona como um Singleton para garantir que todas as sessões
 * de utilizadores ativos sejam armazenadas e geridas num mapa único em memória.
 * * O endpoint utiliza um token para autenticação inicial, mas mapeia as sessões
 * internamente através do ID único do utilizador para garantir a persistência
 * da comunicação mesmo com a renovação de tokens.
 */
@Singleton
@ServerEndpoint("/websocket/chat/{token}")
public class ChatEndpoint {
    private static final Logger logger = LogManager.getLogger(ChatEndpoint.class);

    /** Bean injetado para validar o token e recuperar a identidade do utilizador. */
    @Inject
    private TokenBean tokenBean;

    /** * Mapa que associa o identificador único do utilizador (userId) à sua sessão WebSocket ativa.
     * Permite o endereçamento direto de mensagens sem necessidade de consultar tokens.
     */
    private HashMap<Long, Session> sessions = new HashMap<>();


    /**
     * Envia uma mensagem de texto para um utilizador específico através do seu identificador de sistema (userId).
     * Se a sessão não for encontrada ou ocorrer um erro de I/O, a ligação é considerada
     * inválida e removida do mapa.
     * * @param userId O ID único do utilizador destinatário.
     * @param msg O conteúdo da mensagem (normalmente um JSON) a ser enviado.
     */
    public void send(Long userId, String msg) {
        Session session = sessions.get(userId);

        if (session != null) {
            try {
                session.getBasicRemote().sendText(msg);
                logger.debug("Mensagem enviada com sucesso para o ID: {}", userId);
            } catch (IOException e) {
                logger.error("Falha ao enviar mensagem para o utilizador {}. A remover sessão: {}", userId, e.getMessage());
                sessions.remove(userId);
            }
        } else {
            logger.warn("Tentativa de envio para um utilizador que não está ligado. ID: {}", userId);
        }
    }

    /**
     * Evento disparado no estabelecimento de uma nova ligação.
     * Realiza o 'handshake' de segurança validando o token e, em caso de sucesso,
     * regista a sessão no mapa utilizando o ID do utilizador.
     * * @param session A instância da ligação WebSocket criada.
     * @param token O token de autenticação capturado no path da URL.
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) {
        // Recupera a entidade do utilizador através do token de segurança para validar a ligação
        UserEntity user = tokenBean.getUserEntityByToken(token);

        if (user != null) {
            // Regista a sessão usando o ID único do utilizador como chave primária de endereçamento
            sessions.put(user.getId(), session);
            logger.info("WebSocket: Sessão registada para o Utilizador ID: {}", user.getId());
        } else {
            // Bloqueio de segurança: Se o token for inválido, encerra a ligação imediatamente
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Token Inválido"));
                logger.warn("WebSocket: Tentativa de ligação com token inválido.");
            } catch (IOException e) {
                logger.error("Erro ao encerrar sessão não autorizada: {}", e.getMessage());
            }
        }
    }

    /**
     * Evento disparado quando uma ligação é encerrada, seja por iniciativa do cliente,
     * erro de rede ou encerramento do servidor. Garante a limpeza do mapa de sessões.
     * * @param session A sessão que está a ser encerrada.
     * @param reason O motivo técnico do encerramento.
     * @param token O token utilizado na abertura (usado aqui para identificar o utilizador a remover).
     */
    @OnClose
    public void onClose(Session session, CloseReason reason, @PathParam("token") String token) {

        UserEntity user = tokenBean.getUserEntityByToken(token);
        if (user != null) {
            sessions.remove(user.getId());
            logger.info("Ligação WebSocket terminada para o utilizador {}. Motivo: {}", user.getId(), reason.getReasonPhrase());
        }
    }

    /**
     * Lida com mensagens de texto recebidas pelo canal WebSocket.
     * Embora o fluxo principal de mensagens do projeto utilize a API REST, este método
     * permite interações rápidas ou confirmações de receção.
     * * @param message O conteúdo de texto recebido do cliente.
     * @param session A sessão que originou o envio.
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        logger.info("Mensagem recebida no WebSocket: {}", message);
        try {
            session.getBasicRemote().sendText("Recebido");
        } catch (IOException e) {
            logger.error("Erro ao enviar confirmação de receção via WebSocket: {}", e.getMessage());
        }
    }

    /**
     * Captura e regista erros inesperados durante a transmissão de dados no canal.
     * * @param session A sessão onde a exceção ocorreu.
     * @param error O erro ou exceção lançada.
     */
    @OnError
    public void onError(Session session, Throwable error) {
        logger.error("Ocorreu um erro crítico no canal WebSocket: {}", error.getMessage());
    }
}