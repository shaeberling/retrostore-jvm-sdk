#!/bin/sh

protoc -o ApiProtos.pb ApiProtos.proto
/home/sascha/source/nanopb/generator/nanopb_generator.py ApiProtos.pb
mv ApiProtos.pb.* /home/sascha/source/retrostore-c-sdk/main/proto
rm *.pb
