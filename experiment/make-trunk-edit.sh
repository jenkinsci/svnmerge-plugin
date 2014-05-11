#!/bin/bash -ex
echo "make unrelated trunk edits"
cd ws
pushd trunk
  date +%s.%N  >> added-in-trunk
  svn commit -m "trunk making progress"
popd

