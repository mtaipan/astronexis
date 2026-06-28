package dev.taipan.server_site.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SiteProperties.class)
public class SiteConfig {
}