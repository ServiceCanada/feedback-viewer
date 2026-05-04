package ca.gc.tbs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Service to validate if an IP address is owned by the Government of Canada
 * Uses RDAP (Registration Data Access Protocol) API to check IP ownership
 */
@Service
public class GcIpValidationService {

    private static final Logger logger = LoggerFactory.getLogger(GcIpValidationService.class);
    private static final String RDAP_API_URL = "https://rdap.arin.net/registry/ip/";
    private static final String GC_REGISTRANT_HANDLE = "SSC-299"; // Shared Services Canada handle
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GcIpValidationService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if an IP address is owned by the Government of Canada
     * Results are cached for 24 hours to avoid excessive RDAP API calls
     * 
     * @param ipAddress The IP address to check
     * @return true if the IP is owned by GC, false otherwise
     */
    @Cacheable(value = "gcIpCache", key = "#ipAddress")
    public boolean isGcIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            logger.warn("Received null or empty IP address");
            return false;
        }

        try {
            logger.debug("Checking if IP {} is owned by GC", ipAddress);
            
            String url = RDAP_API_URL + ipAddress;
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null) {
                logger.warn("Received null response from RDAP API for IP {}", ipAddress);
                return false;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode entities = root.get("entities");
            
            if (entities != null && entities.isArray()) {
                boolean isGc = recursiveEntitySearch(entities);
                logger.info("IP {} is {} owned by GC", ipAddress, isGc ? "" : "NOT");
                return isGc;
            }
            
            logger.warn("No entities found in RDAP response for IP {}", ipAddress);
            return false;
            
        } catch (Exception e) {
            logger.error("Error checking IP {} ownership: {}", ipAddress, e.getMessage());
            // Fail closed - if we can't verify, block access
            return false;
        }
    }

    /**
     * Recursively search through entity records to find GC registrant
     * 
     * @param entities JsonNode array of entities
     * @return true if SSC-299 (Shared Services Canada) is found in registrants
     */
    private boolean recursiveEntitySearch(JsonNode entities) {
        if (entities == null || !entities.isArray()) {
            return false;
        }

        for (JsonNode entity : entities) {
            // Check if this entity is a registrant
            JsonNode roles = entity.get("roles");
            if (roles != null && roles.isArray()) {
                boolean isRegistrant = false;
                for (JsonNode role : roles) {
                    if ("registrant".equals(role.asText())) {
                        isRegistrant = true;
                        break;
                    }
                }

                // If this is a registrant, check if it's SSC-299
                if (isRegistrant) {
                    JsonNode handle = entity.get("handle");
                    if (handle != null && GC_REGISTRANT_HANDLE.equals(handle.asText())) {
                        return true;
                    }
                }
            }

            // Recursively check nested entities
            JsonNode nestedEntities = entity.get("entities");
            if (nestedEntities != null && recursiveEntitySearch(nestedEntities)) {
                return true;
            }
        }

        return false;
    }
}
