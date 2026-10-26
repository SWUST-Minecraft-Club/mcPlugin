package com.example.town.model;

public enum PlotKind {
    RESIDENTIAL("居住用地"), COMMERCIAL("营业用地"), PRODUCTION("生产用地"), PUBLIC("公共用地");
    private final String display;
    PlotKind(String display) { this.display=display; }
    public String display() { return display; }
}
