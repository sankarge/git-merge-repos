package org.nibor.git_merge_repos.merger;

import org.nibor.git_merge_repos.log.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.logging.Level;

public class GITCli {

    private final File directory;

    public GITCli(File file) {
        this.directory = file;
    }

    public String describe(String tag) {
        String command = "git describe --contains " + tag;
        String parentTag = null;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c", command}, null, directory);
            try (Scanner s = new Scanner(p.getInputStream()).useDelimiter("\\A")) {
                parentTag = s.hasNext() ? s.next() : "";
            }
        } catch (IOException e) {
            LoggerUtil.PREPARE_LOG.log(Level.SEVERE, "Exception while executing GITCli describe on  " + tag + ". Exception: " + e.getMessage());
        }
        return parentTag;
    }
}