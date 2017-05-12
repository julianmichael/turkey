#!/bin/bash

if [ ! -e "mturk.properties" ]
then
  echo "Missing file: mturk.properties"
  echo "Please add a file named \`mturk.properties\`, consisting of two lines, of the form"
  echo "access_key=XXXXXXXXXXXXXXXXXXXX"
  echo "secret_key=YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"
  echo "where the keys are obtained from your AWS account."
  exit 1
fi

{ echo ":load scripts/initSample.scala" & cat <&0; } | sbt "project turkeySampleJVM" console

