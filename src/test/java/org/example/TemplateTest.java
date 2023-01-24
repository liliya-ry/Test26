package org.example;

import java.io.*;

public class TemplateTest {
    public static void main(String[] args) throws Exception {
        TemplateContext ctx = new TemplateContext();
        WelcomeMessage welcome = new WelcomeMessage("hello world");
        ctx.put("welcome", welcome);

        Student[] students = {
                new Student(1, "Ivan"),
                new Student(2, "Maria"),
                new Student(3, "Nikola")
        };
        ctx.put("students", students);

        Template t = new Template("template.tm");
        OutputStream out = new FileOutputStream("index.html");
        PrintStream ps = new PrintStream(out);
        t.render(ctx, ps);
    }
}
