#!/bin/bash -ex
echo "implementing a feature"
cd ws
pushd branches/feature
  date +%s.%N >> added-in-branch
  svn commit -m "branch making progress"
popd
