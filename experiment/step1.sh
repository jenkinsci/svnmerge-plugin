#!/bin/bash -ex
# create emprty repo
svnadmin create repo
# checkout workspace
svn co file://$PWD/repo ws
cd ws              
# create initial structure  (creates rev.1)
svn mkdir trunk branches && svn commit -m "initial structure"
# create basics  (creates rev.2)
(cd trunk && touch initial && svn add initial && svn commit -m "base form")
# create a feature branch  (creates rev.3)
svn up
svn cp trunk branches/feature
svn commit -m "created a branch"
# make a progress in a branch  (creates rev.4)
pushd branches/feature
  echo a >> added-in-branch
  svn add added-in-branch
  svn commit -m "implementing a feature"
popd
# make a parallel progress in the trunk (rev.5)
pushd trunk
  touch added-in-trunk
  svn add added-in-trunk
  svn commit -m "trunk making progress"
popd
