public class App {
    public String getGreeting() {
        return "Hello world.";
    }

    public static void main(String[] args) {
        System.out.println(new App().getGreeting());
        LoadGenerator gen = new LoadGenerator(0);

        gen.generateLoad();
    }
}
