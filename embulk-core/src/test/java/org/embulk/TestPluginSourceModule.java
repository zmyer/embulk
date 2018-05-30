package org.embulk;

import static org.embulk.plugin.InjectedPluginSource.registerPluginTo;

import com.google.inject.Binder;
import com.google.inject.Module;
import org.embulk.spi.FormatterPlugin;
import org.embulk.spi.MockFormatterPlugin;
import org.embulk.spi.MockParserPlugin;
import org.embulk.spi.ParserPlugin;

public class TestPluginSourceModule implements Module {
    @Override
    public void configure(Binder binder) {
        registerPluginTo(binder, ParserPlugin.class, "mock",
                MockParserPlugin.class);
        registerPluginTo(binder, FormatterPlugin.class, "mock",
                MockFormatterPlugin.class);
    }
}
