package org.embulk.cli;

import com.google.common.collect.ImmutableList;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EmbulkHelpFormatter
        extends HelpFormatter
{
    public EmbulkHelpFormatter()
    {
        super();
        this.setLeftPadding(4);
        this.setSyntaxPrefix("Usage: ");
    }

    public static class EmbulkOptions
            extends Options
    {
        public EmbulkOptions()
        {
            this.optionsWithSeparators = new ArrayList<Option>();
        }

        public Options addSeparator(String separator)
        {
            this.optionsWithSeparators.add(new EmbulkSeparatorDummyOption(separator));
            return this;
        }

        @Override
        public Options addOption(Option opt)
        {
            this.optionsWithSeparators.add(opt);
            return super.addOption(opt);
        }

        public List<Option> getOptionsWithSeparators()
        {
            return ImmutableList.copyOf(this.optionsWithSeparators);
        }

        private List<Option> optionsWithSeparators;
    }

    private static class EmbulkSeparatorDummyOption
            extends Option
    {
        public EmbulkSeparatorDummyOption(String separator)
        {
            super("_", "_");
            this.separator = separator;
        }

        public String getSeparator()
        {
            return this.separator;
        }

        private String separator;
    }

    @Override
    public void printHelp(PrintWriter pw,
                          int width,
                          String cmdLineSyntax,
                          String header,
                          Options options,
                          int leftPad,
                          int descPad,
                          String footer,
                          boolean autoUsage)
    {
        super.printHelp(pw, width, cmdLineSyntax, header, options, leftPad, descPad, footer, autoUsage);
        pw.flush();
    }

    @Override
    protected StringBuffer renderOptions(StringBuffer sb, int width, Options options, int leftPad, int descPad)
    {
        if (!(options instanceof EmbulkOptions)) {
            return super.renderOptions(sb, width, options, leftPad, descPad);
        }

        EmbulkOptions eoptions = (EmbulkOptions)options;

        final String lpad = createPadding(leftPad);
        final String dpad = createPadding(descPad);

        // first create list containing only <lpad>-a,--aaa where
        // -a is opt and --aaa is long opt; in parallel look for
        // the longest opt string this list will be then used to
        // sort options ascending
        int max = 0;
        List<StringBuffer> prefixList = new ArrayList<StringBuffer>();

        List<Option> optList = eoptions.getOptionsWithSeparators();

        for (Option option : optList)
        {
            StringBuffer optBuf = new StringBuffer();

            if (option instanceof EmbulkSeparatorDummyOption) {
                optBuf.append(((EmbulkSeparatorDummyOption)option).getSeparator());
                prefixList.add(optBuf);
                continue;
            }

            if (option.getOpt() == null)
            {
                optBuf.append(lpad).append("    ").append(getLongOptPrefix()).append(option.getLongOpt());
            }
            else
            {
                optBuf.append(lpad).append(getOptPrefix()).append(option.getOpt());

                if (option.hasLongOpt())
                {
                    optBuf.append(", ").append(getLongOptPrefix()).append(option.getLongOpt());
                }
            }

            if (option.hasArg())
            {
                String argName = option.getArgName();
                if (argName != null && argName.length() == 0)
                {
                    // if the option has a blank argname
                    optBuf.append(' ');
                }
                else
                {
                    optBuf.append(option.hasLongOpt() ? getLongOptSeparator() : " ");
                    optBuf.append(argName != null ? option.getArgName() : getArgName());
                }
            }

            prefixList.add(optBuf);
            max = optBuf.length() > max ? optBuf.length() : max;
        }

        int x = 0;

        for (Iterator<Option> it = optList.iterator(); it.hasNext();)
        {
            Option option = it.next();

            if (option instanceof EmbulkSeparatorDummyOption) {
                String separator = prefixList.get(x++).toString();
                sb.append(separator);
                sb.append(getNewLine());
                continue;
            }

            StringBuilder optBuf = new StringBuilder(prefixList.get(x++).toString());

            if (optBuf.length() < max)
            {
                optBuf.append(createPadding(max - optBuf.length()));
            }

            optBuf.append(dpad);

            int nextLineTabStop = max + descPad;

            if (option.getDescription() != null)
            {
                optBuf.append(option.getDescription());
            }

            renderWrappedText(sb, width, nextLineTabStop, optBuf.toString());

            if (it.hasNext())
            {
                sb.append(getNewLine());
            }
        }

        return sb;
    }
}
