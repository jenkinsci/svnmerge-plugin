#!/bin/bash -ex
cd ws

echo "merge feature branch"
svn up
pushd trunk
  svn merge "$@" '^/branches/feature'
  svn commit -m "merged branch"
  svn up
  rev=$(svn info | grep Revision | cut -f2 -d ' ')
popd

pushd branches/feature
  svn up
  svn merge --record-only -c $rev '^/trunk'
  svn commit -m "blocking $rev from being merged again"
popd

