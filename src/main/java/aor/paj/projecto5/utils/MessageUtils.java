package aor.paj.projecto5.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilitário para gestão de mensagens internacionalizadas (i18n) via JSON no Backend.
 */
public class MessageUtils {

    private static final Logger logger = LogManager.getLogger(MessageUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    
    // ConcurrentHashMap é essencial para evitar erros em ambiente multi-thread (Servidor)
    private static final Map<String, JsonNode> cache = new ConcurrentHashMap<>();

    /**
     * Obtém uma mensagem traduzida do JSON com suporte a chaves aninhadas.
     */
    public static String getMessage(String key, String language) {
        try {
            // Normalizar idioma (ex: "pt-PT" -> "pt")
            String lang = (language != null && language.length() >= 2) 
                          ? language.substring(0, 2).toLowerCase() 
                          : "pt";
            
            JsonNode root = loadResource(lang);
            if (root == null) return key;

            String[] parts = key.split("\\.");
            JsonNode current = root;
            for (String part : parts) {
                current = current.get(part);
                if (current == null) return key;
            }

            return current.asText();
        } catch (Exception e) {
            logger.error("Erro ao obter mensagem i18n para chave: " + key, e);
            return key;
        }
    }

    private static JsonNode loadResource(String lang) {
        // Se já está em cache, retorna imediatamente
        if (cache.containsKey(lang)) {
            return cache.get(lang);
        }

        String fileName = "/messages_" + lang + ".json";
        try (InputStream is = MessageUtils.class.getResourceAsStream(fileName)) {
            if (is == null) {
                logger.warn("Ficheiro de tradução não encontrado: " + fileName);
                return null;
            }
            JsonNode node = mapper.readTree(is);
            cache.put(lang, node);
            return node;
        } catch (Exception e) {
            logger.error("Erro ao carregar ficheiro JSON de tradução: " + fileName, e);
            return null;
        }
    }
}
