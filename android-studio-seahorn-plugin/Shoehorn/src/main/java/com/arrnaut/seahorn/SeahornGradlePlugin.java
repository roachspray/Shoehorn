package com.arrnaut.seahorn;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class SeahornGradlePlugin implements Plugin<Project> {
	static final String TASK_NAME = "seahornTask";

    @Override
    public void apply(Project t) {
        t.getTasks().create(TASK_NAME, SeahornTask.class);
    }
}
