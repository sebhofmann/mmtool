package de.vzg.mmtool.config;

import java.util.ArrayList;
import java.util.List;

public class Config {

    public Config() {
        this.applications = new ArrayList<>();
        this.projects = new ArrayList<>();
    }

    public List<ApplicationSpecificConfig> applications;

    public List<String> projects;

}
