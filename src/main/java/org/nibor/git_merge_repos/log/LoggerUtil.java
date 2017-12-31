package org.nibor.git_merge_repos.log;

import org.nibor.git_merge_repos.merger.RepoMerger;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by sankarge on 12/21/17.
 */
public class LoggerUtil {

    public static final Logger log = Logger.getLogger(LoggerUtil.class.getName());

    {
        try {
            configLogger();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void configLogger() throws IOException {
        FileHandler fh = new FileHandler(RepoMerger.class.getSimpleName() + ".log", true);
        fh.setLevel(Level.ALL);
        fh.setFormatter(new CustomLogFormatter());
        log.addHandler(fh);
        log.setUseParentHandlers(false);
    }
}
