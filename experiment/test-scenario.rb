#!/usr/bin/ruby

def run(cmd)
  puts "$ #{cmd}"
  if !system(cmd) then
    throw Error.new("command failed")
  end
end

$commands = { "I" => "./integrate.sh", "i" => "./integrate.sh --reintegrate", "R" => "./rebase.sh", "r" => "./rebase.sh --reintegrate", "-" => "./make-edit.sh" }


run "rm -rf ws repo"
run "./step1.sh"

$i=0

def pattern(str)
  str.chars do |c|
    $i+=1
    puts "==== step #{$i} ====="
    run $commands[c]
  end
end

# using Ri and R as a primitive, exhausitve combination. this works
#pattern "-Ri-Ri-R-R-Ri"

# trying them in random orders. this works
#50.times do
#  pattern ["-Ri","-R"][rand(2)]
#end

# but "ii" fails
# pattern "ii"

pattern "ii"


# Note: http://www.scons.org/wiki/BranchAndMerge talks about somewhat different practice
