package org.nibor.git_merge_repos.util;

import org.nibor.git_merge_repos.log.LoggerUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;
import java.util.logging.Level;

/**
 * Created by sankarge on 1/9/18.
 */
public abstract class FileUtil {

    public static final File PROPERTIES = new File("parentTag.properties");

    public static void saveMap(Map<String, String> map) {
        SortedProperties props = new SortedProperties();
        map.forEach((key, value) -> props.put(key, value == null ? "" : value));

        try (FileOutputStream os = new FileOutputStream(PROPERTIES)) {
            props.store(os, null);
        } catch (Exception e) {
            LoggerUtil.PREPARE_LOG.log(Level.SEVERE, "Unable to write properties file.");
        }
    }

    public static Map loadMap() {
        Map<String, String> tagParentInfo = new TreeMap<>();
        try (FileInputStream is = new FileInputStream(PROPERTIES)) {
            Properties props = new Properties();
            props.load(is);
            props.forEach((key, value) -> {
                        String val = value.equals("") ? null : (String) value;
                        tagParentInfo.put((String) key, val);
                    }
            );
        } catch (Exception e) {
            LoggerUtil.PREPARE_LOG.log(Level.SEVERE, "Unable to read properties file.");
        }
        return tagParentInfo;
    }

    static class SortedProperties extends Properties {
        public Enumeration keys() {
            Enumeration keysEnum = super.keys();
            Vector<String> keyList = new Vector<>();
            while (keysEnum.hasMoreElements()) {
                keyList.add((String) keysEnum.nextElement());
            }
            Collections.sort(keyList);
            return keyList.elements();
        }
    }
}
