package dev.taipan.server_site.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки доставки VIP на игровой сервер по RCON.
 * Реальные значения — из ENV (см. application.properties: ${RCON_*}).
 */
@ConfigurationProperties(prefix = "rcon")
public class RconProperties {

    /** Включена ли RCON-доставка. Если false — гранты копятся в очереди для ручной/плагинной выдачи. */
    private boolean enabled = false;

    private String host = "127.0.0.1";
    private int port = 25575;
    private String password = "";

    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 3000;

    /**
     * Шаблон команды выдачи VIP. Плейсхолдеры: %nick%, %days%, %group%.
     * По умолчанию — LuckPerms. Пример итоговой команды:
     *   lp user Steve parent addtemp VIP 30d accumulate
     */
    private String vipCommand = "lp user %nick% parent addtemp %group% %days%d accumulate";

    /** Сколько раз пытаться доставить грант, прежде чем пометить FAILED. */
    private int maxAttempts = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public String getVipCommand() { return vipCommand; }
    public void setVipCommand(String vipCommand) { this.vipCommand = vipCommand; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
}
