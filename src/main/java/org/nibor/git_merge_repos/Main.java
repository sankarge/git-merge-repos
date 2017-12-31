package org.nibor.git_merge_repos;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.nibor.git_merge_repos.merger.RepoMerger;
import org.nibor.git_merge_repos.vo.MergedRef;
import org.nibor.git_merge_repos.vo.SubtreeConfig;

/**
 * Main class for merging repositories via command-line.
 */
public class Main {

	private static Pattern REPO_AND_DIR = Pattern.compile("(.*):([^:]+)");

	public static void main(String[] args) throws IOException, GitAPIException, URISyntaxException {
		List<SubtreeConfig> subtreeConfigs = new ArrayList<>();

		for (String arg : args) {
			Matcher matcher = REPO_AND_DIR.matcher(arg);
			if (matcher.matches()) {
				String repositoryUrl = matcher.group(1);
				String directory = matcher.group(2);
				SubtreeConfig config = new SubtreeConfig(directory, new URIish(repositoryUrl));
				subtreeConfigs.add(config);
			} else {
				exitInvalidUsage("invalid argument '" + arg
						+ "', expected '<repository_url>:<target_directory>'");
			}
		}

		if (subtreeConfigs.isEmpty()) {
			exitInvalidUsage("usage: program <repository_url>:<target_directory>...");
		}

		//todo: Make the following path configurable if needed
		File outputDirectory = new File("/udir/sankarge/git/merge/output/merged");
		String outputPath = outputDirectory.getPath();
		System.out.println("Started merging " + subtreeConfigs.size()
				+ " repositories into one, output directory: " + outputPath);

		long start = System.currentTimeMillis();
		RepoMerger merger = new RepoMerger(outputPath, subtreeConfigs);
		merger.run();
		long end = System.currentTimeMillis();

		long timeMs = (end - start);
		printIncompleteRefs(merger.getMergedRefs());
		System.out.println("Done, took " + (timeMs/1000)/60 + " mins");
		System.out.println("Merged repository: " + outputPath);
	}

	private static void printIncompleteRefs(List<MergedRef> mergedRefs) {
		for (MergedRef mergedRef : mergedRefs) {
			if (!mergedRef.getConfigsWithoutRef().isEmpty()) {
				System.out.println(mergedRef.getRefType() + " '" + mergedRef.getRefName()
						+ "' was not in: " + join(mergedRef.getConfigsWithoutRef()));
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
		System.err.println(message);
		System.exit(64);
	}
}