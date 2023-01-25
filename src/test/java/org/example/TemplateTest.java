package org.example;

import java.io.IOException;

public class TemplateTest {
    public static void main(String[] args) throws IOException, NoSuchFieldException, IllegalAccessException {
        TemplateContext ctx = new TemplateContext();
        WelcomeMessage welcome = new WelcomeMessage("hello world");
        ctx.put("welcome", welcome);

        Student[] students = {
                new Student(1, "Ivan"),
                new Student(2, "Maria"),
                new Student(3, "Nikola")
        };
        ctx.put("students", students);

        Template t = new Template("D:\\IdeaProjects\\Test26\\src\\main\\resources\\template.tm");
        t.render(ctx, System.out);
    }
}
