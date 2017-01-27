puts "[START] embulk_main.rb:"
ARGV.each do |arg|
  print "  <#{arg}>"
end
puts ""
puts "  ..."
puts ""

require 'embulk/command/embulk_run'
Embulk.run(ARGV)
puts "[ END ] embulk_main.rb."
