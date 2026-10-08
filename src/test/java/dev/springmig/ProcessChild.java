package dev.springmig;

public class ProcessChild {
    public static void main(String[] args) throws Exception {
        if(args[0].equals("log")) {System.out.print("x".repeat(2*Processes.LOG_LIMIT));return;}
        System.out.println("pid="+ProcessHandle.current().pid());System.out.flush();
        if(args[0].equals("parent")) {
            var command=Processes.javaCommand();command.addAll(java.util.List.of("-cp",System.getProperty("java.class.path"),ProcessChild.class.getName(),"sleep"));
            Process child=Processes.builder(command,java.nio.file.Path.of(".")).inheritIO().start();System.out.println("child="+child.pid());System.out.flush();child.waitFor();
        }else Thread.sleep(60000);
    }
}
