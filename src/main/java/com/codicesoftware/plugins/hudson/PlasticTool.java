package com.codicesoftware.plugins.hudson;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.util.ForkOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.logging.Logger;

/**
 * Class that encapsulates the Plastic SCM command client.
 *
 * @author Erik Ramfelt
 * @author Dick Porter
 */
public class PlasticTool {
    private final String executable;
    private Launcher launcher;
    private TaskListener listener;
    private FilePath workspace;

    private static final Logger logger = Logger.getLogger(PlasticTool.class.getName());

    public PlasticTool(String executable, Launcher launcher, TaskListener listener,
            FilePath workspace) {
        this.executable = executable;
        this.launcher = launcher;
        this.listener = listener;
        this.workspace = workspace;
    }

    public TaskListener getListener() {
        return listener;
    }

    /**
     * Execute the arguments, and return the console output as a Reader
     * @param arguments arguments to send to the command-line client.
     * @return a Reader containing the console output
     * @throws IOException
     * @throws InterruptedException
     */
    public Reader execute(String[] arguments) throws IOException, InterruptedException {
        return execute(arguments, null);
    }

    /**
     * Execute the arguments, and return the console output as a Reader
     * @param arguments arguments to send to the command-line client.
     * @param masks which of the commands that should be masked from the console.
     * @return a Reader containing the console output
     * @throws IOException
     * @throws InterruptedException
     */
    public Reader execute(String[] arguments, boolean[] masks) throws IOException, InterruptedException {
        String[] toolArguments = new String[arguments.length + 1];
        toolArguments[0] = executable;
        for (int i = 0; i < arguments.length; i++) {
            toolArguments[i + 1] = arguments[i];
        }

        boolean[] toolMasks = new boolean[arguments.length + 1];
        if (masks != null) {
            toolMasks = new boolean[masks.length + 1];
            toolMasks[0] = false;
            for (int i = 0; i < masks.length; i++) {
                toolMasks[i + 1] = masks[i];
            }
        }

        ByteArrayOutputStream consoleStream = new ByteArrayOutputStream();
        Proc proc = launcher.launch().cmds(toolArguments).masks(toolMasks)
                .stdout(new ForkOutputStream(consoleStream, listener.getLogger()))
                .pwd(workspace).start();
        consoleStream.close();

        int result = proc.join();
        logger.fine(String.format("The cm command '%s' returned with an error code of %d", toolArguments[1], result));
        if (result == 0) {
            return new InputStreamReader(new ByteArrayInputStream(consoleStream.toByteArray()));
        } else {
            listener.fatalError(String.format("Executable returned an unexpected result code [%d]", result));
            throw new AbortException();
        }
    }
}