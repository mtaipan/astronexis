package dev.taipan.server_site;

import dev.taipan.server_site.config.RconProperties;
import dev.taipan.server_site.config.SiteProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({SiteProperties.class, RconProperties.class})
public class ServerSiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerSiteApplication.class, args);
    }
}
