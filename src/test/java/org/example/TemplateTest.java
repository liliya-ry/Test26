package org.example;

public class TemplateTest {
    TemplateContext ctx = new TemplateContext();
    WelcomeMessage welcome = new WelcomeMessage("hello world");
ctx.put("welcome", welcome);

    Student students[] = {
            new Student(1, "Ivan"),
            new Student(2, "Maria"),
            new Student(3, "Nikola")
    };
ctx.put("students", students);

    Template t = new Template("template.tm");
    PrintWriter out = System.out;
t.redner(ctx, out);
}
