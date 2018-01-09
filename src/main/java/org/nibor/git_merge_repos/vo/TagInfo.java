package org.nibor.git_merge_repos.vo;

import org.nibor.git_merge_repos.log.LoggerUtil;

import java.util.Collection;
import java.util.Date;
import java.util.Optional;

public class TagInfo {

    private String name;

    private Date date;

    private String repoName;

    public TagInfo(String name, Date date, String repoName) {
        this.name = name;
        this.date = date;
        this.repoName = repoName;
    }

    public String getName() {
        return name;
    }

    public static String findLatestTag(String tag, Collection<TagInfo> parentTagInfoSet) {
        parentTagInfoSet.forEach(tagInfo -> {
            LoggerUtil.PREPARE_LOG.info(tagInfo.repoName + " => " + tagInfo.getName() + ", " + tagInfo.date);
        });
        Optional<TagInfo> tagInfoOptional = parentTagInfoSet
                .stream()
                .filter(tagInfo -> !tagInfo.getName().equals(tag))
                .filter(tagInfo -> tagInfo.date != null)
                .sorted((o1, o2) -> o2.date.compareTo(o1.date))
                .findFirst();
        if (tagInfoOptional.isPresent()) {
            String parentTag = tagInfoOptional.get().getName();
            LoggerUtil.PREPARE_LOG.info("findLatestTag: parent tag for " + tag + " is " + parentTag + "\n");
            return parentTag;
        } else {
            LoggerUtil.PREPARE_LOG.info("findLatestTag: Unable to collect parent tag for " + tag + "\n");
            return null;
        }
    }
}