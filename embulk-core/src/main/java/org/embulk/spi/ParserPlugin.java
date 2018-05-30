package org.embulk.spi;

import org.embulk.config.ConfigSource;
import org.embulk.config.TaskSource;

public interface ParserPlugin {
    interface Control {
        void run(TaskSource taskSource, Schema schema);
    }

    void transaction(ConfigSource config, ParserPlugin.Control control);

    void run(TaskSource taskSource, Schema schema,
            FileInput input, PageOutput output);
}
