package org.nibor.git_merge_repos.log;

import org.nibor.git_merge_repos.merger.ParentTagCollector;
import org.nibor.git_merge_repos.merger.RepoMerger;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by sankarge on 12/21/17.
 */
public class LoggerUtil {

    public static final Logger PREPARE_LOG = Logger.getLogger(ParentTagCollector.class.getName());

    public static final Logger MERGE_LOG = Logger.getLogger(RepoMerger.class.getName());

    static {
        try {
            configLogger();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void configLogger() throws IOException {
        FileHandler mergeFilerHandler = new FileHandler("merge.log", false);
        mergeFilerHandler.setLevel(Level.ALL);
        mergeFilerHandler.setFormatter(new CustomLogFormatter());
        MERGE_LOG.addHandler(mergeFilerHandler);
        MERGE_LOG.setUseParentHandlers(false);

        FileHandler prepareFileHandler = new FileHandler("prepare.log", false);
        prepareFileHandler.setLevel(Level.ALL);
        prepareFileHandler.setFormatter(new CustomLogFormatter());
        PREPARE_LOG.addHandler(prepareFileHandler);
        PREPARE_LOG.setUseParentHandlers(false);
    }
}