package com.example.plaidapp.plaid_app.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @PostConstruct
    public void logDataSourceConfig() {
        logger.info("=== DataSource Configuration ===");
        logger.info("URL: {}", datasourceUrl);
        logger.info("Username: {}", datasourceUsername);
        // Don't log password for security, but check if it contains unresolved placeholder
        if (datasourcePassword != null && datasourcePassword.startsWith("${") && datasourcePassword.endsWith("}")) {
            logger.error("WARNING: Database password appears to be unresolved! Check if DATABASE_PASSWORD environment variable is set.");
        } else {
            logger.info("Password: [REDACTED - {} characters]", datasourcePassword != null ? datasourcePassword.length() : 0);
        }
        
        // Check for unresolved placeholders
        if (datasourceUrl.contains("${") || datasourceUsername.contains("${")) {
            logger.error("ERROR: Unresolved environment variable placeholders detected!");
            logger.error("This usually means the environment variables are not set or not accessible.");
            logger.error("URL contains unresolved: {}", datasourceUrl.contains("${"));
            logger.error("Username contains unresolved: {}", datasourceUsername.contains("${"));
        }
        logger.info("================================");
    }
}

