package ca.gc.tbs.filter;

import ca.gc.tbs.service.GcIpValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Filter to restrict access to Government of Canada IP addresses only
 * This filter runs before authentication to ensure only GC networks can access the application
 */
@Component
@Order(1) // Run before other filters
public class GcIpFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(GcIpFilter.class);

    @Autowired
    private GcIpValidationService gcIpValidationService;

    @Value("${gc.ip.filter.enabled:true}")
    private boolean filterEnabled;

    @Value("${gc.ip.filter.whitelist:}")
    private String whitelistIps;

    @Value("${gc.ip.filter.whitelist.file:}")
    private String whitelistFilePath;

    private Set<String> fileWhitelistIps = new HashSet<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip filter if disabled (for local development)
        if (!filterEnabled) {
            logger.debug("GC IP filter is disabled");
            chain.doFilter(request, response);
            return;
        }

        // Skip filter for health check endpoints (ALB health checks)
        String requestPath = httpRequest.getRequestURI();
        if ("/health".equals(requestPath) || "/actuator/health".equals(requestPath)) {
            logger.debug("Skipping GC IP filter for health check endpoint: {}", requestPath);
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIpAddress(httpRequest);
        logger.debug("Request from IP: {}", clientIp);

        // Check if IP is in whitelist
        if (isWhitelisted(clientIp)) {
            logger.debug("IP {} is authorized (whitelisted)", clientIp);
            chain.doFilter(request, response);
            return;
        }

        // Check if IP is owned by GC
        if (gcIpValidationService.isGcIp(clientIp)) {
            logger.debug("IP {} is authorized (GC-owned)", clientIp);
            chain.doFilter(request, response);
        } else {
            logger.warn("Access denied for non-GC IP: {}", clientIp);
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("text/html; charset=UTF-8");
            httpResponse.getWriter().write(
                "<!DOCTYPE html>" +
                "<html lang=\"en\">" +
                "<head>" +
                "    <meta charset=\"UTF-8\">" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "    <title>Access Denied</title>" +
                "    <style>" +
                "        body { font-family: Arial, sans-serif; margin: 50px; }" +
                "        h1 { color: #d9534f; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <h1>Access Denied</h1>" +
                "    <p>This application is only accessible from Government of Canada networks.</p>" +
                "    <p>If you believe you should have access, please contact your system administrator.</p>" +
                "</body>" +
                "</html>"
            );
        }
    }

    /**
     * Extract client IP address from request
     * Handles X-Forwarded-For header for requests behind load balancer
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerCandidates = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : headerCandidates) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Check if IP is in the whitelist (property or file-based)
     */
    private boolean isWhitelisted(String ip) {
        // Check property-based whitelist
        if (whitelistIps != null && !whitelistIps.trim().isEmpty()) {
            String[] whitelist = whitelistIps.split(",");
            for (String whitelistedIp : whitelist) {
                if (whitelistedIp.trim().equals(ip)) {
                    return true;
                }
            }
        }

        // Check file-based whitelist
        if (!fileWhitelistIps.isEmpty() && fileWhitelistIps.contains(ip)) {
            return true;
        }

        return false;
    }

    /**
     * Load whitelist IPs from file
     * Supports comments (lines starting with #)
     * Supports both comma-separated and newline-separated formats
     */
    private void loadWhitelistFromFile() {
        if (whitelistFilePath == null || whitelistFilePath.trim().isEmpty()) {
            return;
        }

        File file = new File(whitelistFilePath);
        if (!file.exists()) {
            logger.warn("Whitelist file not found: {}", whitelistFilePath);
            return;
        }

        fileWhitelistIps.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Handle comma-separated IPs
                if (line.contains(",")) {
                    String[] ips = line.split(",");
                    for (String ip : ips) {
                        String trimmedIp = ip.trim();
                        if (!trimmedIp.isEmpty()) {
                            fileWhitelistIps.add(trimmedIp);
                        }
                    }
                } else {
                    // Single IP per line
                    fileWhitelistIps.add(line);
                }
            }
            logger.info("Loaded {} IP addresses from whitelist file: {}", 
                        fileWhitelistIps.size(), whitelistFilePath);
        } catch (IOException e) {
            logger.error("Error reading whitelist file {}: {}", whitelistFilePath, e.getMessage());
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Load IPs from file if configured
        loadWhitelistFromFile();
        
        logger.info("GC IP Filter initialized - filter enabled: {}, property whitelist: {}, file whitelist: {} IPs", 
                    filterEnabled, 
                    whitelistIps != null && !whitelistIps.isEmpty() ? "configured" : "none",
                    fileWhitelistIps.size());
    }

    @Override
    public void destroy() {
        logger.info("GC IP Filter destroyed");
    }
}
