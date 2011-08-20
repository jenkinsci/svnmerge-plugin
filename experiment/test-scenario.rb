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

# Fails
#RIR
#rr fails but RR works
#RIrIrr
#RIrIrrII
#RIrIrRIR

# using ir and R as a primitive, exhausitve combination. this works
#pattern "-ir-ir-R-R-ir"

# trying them in random orders. this works
#50.times do
#  pattern ["-ir","-R"][rand(2)]
#end

pattern "-rr"


# Note: http://www.scons.org/wiki/BranchAndMerge talks about somewhat different practice
