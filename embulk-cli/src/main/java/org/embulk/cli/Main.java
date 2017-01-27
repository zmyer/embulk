package org.embulk.cli;

import java.net.URISyntaxException;

public class Main
{
    public static void main(String[] args)
    {
        System.out.println("[START] org.embulk.cli.Main:");
        for (String arg: args) {
            System.out.printf("  <%s>", arg);
        }
        System.out.println("");
        System.out.println("  ...");
        System.out.println("");
        // $ java -jar jruby-complete.jar embulk-core.jar!/embulk/command/embulk_bundle.rb "$@"
        String[] jrubyArgs = new String[args.length + 1];
        int i;
        for (i = 0; i < args.length; i++) {
            if (args[i].startsWith("-R")) {
                jrubyArgs[i] = args[i].substring(2);
            } else {
                break;
            }
        }
        jrubyArgs[i] = getScriptPath();
        for (; i < args.length; i++) {
            jrubyArgs[i+1] = args[i];
        }
        org.jruby.Main.main(jrubyArgs);
        System.out.println("[ END ] org.embulk.cli.Main.");
    }

    private static String getScriptPath()
    {
        String resourcePath;
        try {
            resourcePath = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().toString() + "!/";
        }
        catch (URISyntaxException ex) {
            resourcePath = "uri:classloader:/";
        }
        return resourcePath + "embulk/command/embulk_bundle.rb";
    }
}
