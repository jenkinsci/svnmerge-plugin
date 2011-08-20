#!/bin/bash -ex
# sync trunk to branch (creates rev.6)
cd ws
svn up
pushd branches/feature
  svn merge --reintegrate '^/trunk'
  svn commit -m "brought in trunk"
popd

# merge feature branch (creates rev.7)
svn up
pushd trunk
  svn merge --reintegrate '^/branches/feature'
  svn commit -m "merged branch"
popd

# make more progress in a branch (creates rev.8)
pushd branches/feature
  echo a >> added-in-branch
  svn commit -m "implementing a feature"
popd

echo "merge back again (creates rev.9)"
svn up
pushd trunk
  svn merge --reintegrate '^/branches/feature'
  svn commit -m "merged branch"
popd

# why does the above result in a conflict?
#--- Merging differences between repository URLs into '.':
#   C added-in-branch
#Summary of conflicts:
#  Tree conflicts: 1
# but "svn merge" works


