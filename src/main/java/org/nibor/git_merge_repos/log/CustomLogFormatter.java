package org.nibor.git_merge_repos.log;

import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;


public class CustomLogFormatter extends Formatter {

    private final Date dat = new Date();

    private static final String seperator = " : ";

    public synchronized String format(LogRecord record) {
        dat.setTime(record.getMillis());
        return dat + seperator + record.getLevel().getName() + seperator + record.getMessage() + "\n";
    }
}