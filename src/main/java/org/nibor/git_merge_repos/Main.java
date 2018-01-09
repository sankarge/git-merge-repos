package org.nibor.git_merge_repos;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.nibor.git_merge_repos.merger.ParentTagCollector;
import org.nibor.git_merge_repos.merger.RepoMerger;
import org.nibor.git_merge_repos.vo.MergedRef;
import org.nibor.git_merge_repos.vo.SubtreeConfig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.nibor.git_merge_repos.log.LoggerUtil.MERGE_LOG;
import static org.nibor.git_merge_repos.log.LoggerUtil.PREPARE_LOG;

/**
 * Main class for merging repositories via command-line.
 */
public class Main {

    private static Pattern REPO_AND_DIR = Pattern.compile("(.*):([^:]+)");

    public static void main(String[] args) throws IOException, GitAPIException, URISyntaxException, IllegalAccessException {
        validate(args);

        String option = args[0];
        String outputPath = args[1];
        List<SubtreeConfig> subtreeConfigs = getSubtreeConfigs(Arrays.copyOfRange(args, 2, args.length));

        if (option.equalsIgnoreCase("prepare")) {
            prepare(subtreeConfigs, outputPath);
        } else if (option.equalsIgnoreCase("merge")) {
            merge(subtreeConfigs, outputPath);
        } else {
            throw new IllegalAccessException("Invalid option given: " + option + ". Expected prepare|merge");
        }
    }

    private static void validate(String[] args) {
        if (args.length < 5) {
            logExample();
            exitInvalidUsage("mandatory arguments missing: " + Arrays.toString(args) + ". Expected format '<option [prepare|merge]> <outputDir> <repository_url>:<target_directory>*'");
        }
    }

    private static void prepare(List<SubtreeConfig> subtreeConfigs, String outputPath) throws IOException, GitAPIException {
        PREPARE_LOG.log(Level.INFO, "Started fetching and gathering parent tag information..");
        long start = System.currentTimeMillis();
        ParentTagCollector parentTagFinder = new ParentTagCollector(outputPath, subtreeConfigs);
        parentTagFinder.collect();

        long end = System.currentTimeMillis();
        long timeMs = (end - start);
        PREPARE_LOG.log(Level.INFO, "Done, fetching and gathering parent tag information took " + (timeMs / 1000) / 60 + " mins");
    }

    private static List<SubtreeConfig> getSubtreeConfigs(String[] args) throws URISyntaxException {
        List<SubtreeConfig> subtreeConfigs = new ArrayList<>();
        for (String arg : args) {
            Matcher matcher = REPO_AND_DIR.matcher(arg);
            if (matcher.matches()) {
                String repositoryUrl = matcher.group(1);
                String directory = matcher.group(2);
                SubtreeConfig config = new SubtreeConfig(directory, new URIish(repositoryUrl));
                subtreeConfigs.add(config);
            } else {
                exitInvalidUsage("invalid argument '" + arg + "', expected '<repository_url>:<target_directory>'");
            }
        }

        if (subtreeConfigs.isEmpty()) {
            exitInvalidUsage("usage: program <repository_url>:<target_directory>...");
        }
        return subtreeConfigs;
    }

    private static void merge(List<SubtreeConfig> subtreeConfigs, String outputPath) throws IOException, GitAPIException {
        long start = System.currentTimeMillis();
        RepoMerger merger = new RepoMerger(outputPath, subtreeConfigs);
        merger.run();

        long end = System.currentTimeMillis();
        long timeMs = (end - start);
        postMerge(outputPath, merger, timeMs);
    }

    private static void postMerge(String outputPath, RepoMerger merger, long timeMs) {
        printIncompleteRefs(merger.getMergedRefs());
        log(Level.INFO, "Done, took " + (timeMs / 1000) / 60 + " mins");
        log(Level.INFO, "Merged repository: " + outputPath);
    }

    private static void printIncompleteRefs(List<MergedRef> mergedRefs) {
        for (MergedRef mergedRef : mergedRefs) {
            if (!mergedRef.getConfigsWithoutRef().isEmpty()) {
                log(Level.INFO, mergedRef.getRefType() + " '" + mergedRef.getRefName() + "' was not in: " + join(mergedRef.getConfigsWithoutRef()));
            }
        }
    }

    private static String join(Collection<SubtreeConfig> configs) {
        StringBuilder sb = new StringBuilder();
        for (SubtreeConfig config : configs) {
            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append(config.getRemoteName());
        }
        return sb.toString();
    }

    private static void exitInvalidUsage(String message) {
        log(Level.SEVERE, message);
        System.exit(64);
    }

    private static void logExample() {
        log(Level.SEVERE, "Example:");
        log(Level.SEVERE, "prepare /udir/sankarge/git/merge/output/merged /udir/sankarge/git/merge/input/bare/sdcna-super:. ." +
                "/udir/sankarge/git/merge/input/bare/sdcna:. /udir/sankarge/git/merge/input/bare/commons:. " +
                "/udir/sankarge/git/merge/input/bare/dsl:. /udir/sankarge/git/merge/input/bare/drdsl-webapp:. " +
                "/udir/sankarge/git/merge/input/bare/platform:. /udir/sankarge/git/merge/input/bare/optical:. " +
                "/udir/sankarge/git/merge/input/bare/ipm:. /udir/sankarge/git/merge/input/bare/na-birt:.");
    }

    private static void log(Level level, String message) {
        MERGE_LOG.log(level, message);
    }
}