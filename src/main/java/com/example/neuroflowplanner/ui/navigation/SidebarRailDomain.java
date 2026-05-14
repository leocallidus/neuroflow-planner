package com.example.neuroflowplanner.ui.navigation;

/**
 * Primary domains for the two-tier left navigation rail.
 */
public enum SidebarRailDomain {
    WORK("work", "Рабочее", "Рабочие сценарии", "Рабочее", "mdi:briefcase-outline"),
    RECENT("recent", "Недавние", "Недавние действия", "Недавние действия", "mdi:history"),
    TOOLS("tools", "Инструменты", "Инструменты и утилиты", "Инструменты", "mdi:tools"),
    ANALYTICS("analytics", "Аналитика", "Аналитика и ИИ", "Аналитика и ИИ", "mdi:chart-line"),
    SYSTEM("system", "Система", "Настройки и системные команды", "Система", "mdi:cog-outline");

    private final String id;
    private final String label;
    private final String railTooltipLabel;
    private final String contextHeaderLabel;
    private final String icon;

    SidebarRailDomain(
        String id,
        String label,
        String railTooltipLabel,
        String contextHeaderLabel,
        String icon
    ) {
        this.id = id;
        this.label = label;
        this.railTooltipLabel = railTooltipLabel;
        this.contextHeaderLabel = contextHeaderLabel;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String railTooltipLabel() {
        return railTooltipLabel;
    }

    public String contextHeaderLabel() {
        return contextHeaderLabel;
    }

    public String icon() {
        return icon;
    }
}
