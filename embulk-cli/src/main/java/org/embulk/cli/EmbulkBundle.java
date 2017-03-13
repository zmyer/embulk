package org.embulk.cli;

public class EmbulkBundle
{
    public static void bundle()
    {
        //   # default GEM_HOME is ~/.embulk/jruby/1.9/. If -b option is set,
        //   # GEM_HOME is already set by embulk/command/embulk_main.rb
        //   ENV.delete('EMBULK_BUNDLE_PATH')
        //   user_home = java.lang.System.properties["user.home"] || ENV['HOME']
        //   unless user_home
        //     raise "HOME environment variable is not set."
        //   end
        //   ENV['GEM_HOME'] = File.expand_path File.join(user_home, '.embulk', Gem.ruby_engine, RbConfig::CONFIG['ruby_version'])
        //   ENV['GEM_PATH'] = ''
        //
        //   ENV.delete('BUNDLE_GEMFILE')
        //   Gem.clear_paths  # force rubygems to reload GEM_HOME
        //
        //   $LOAD_PATH << File.expand_path('../../', File.dirname(__FILE__))
        //   require 'embulk/command/embulk_main'
    }
}
