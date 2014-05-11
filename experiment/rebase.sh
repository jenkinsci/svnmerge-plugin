#!/bin/bash -ex
cd ws

echo "rebasing"
svn up
pushd branches/feature
  svn merge "$@" '^/trunk'
  svn commit -m "brought in trunk"
popd

