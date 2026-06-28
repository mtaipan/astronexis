package dev.taipan.server_site.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    private final SiteProperties site;

    public GlobalModelAttributes(SiteProperties site) {
        this.site = site;
    }

    @ModelAttribute("site")
    public SiteProperties site() {
        return site;
    }
}