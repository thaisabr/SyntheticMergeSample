#!/usr/bin/env bash
#
# Exit the shell script immediately if any of the subsequent commands fails.
# immediately
set -e
#
export MAJOR_OPT="-J-Dmajor.export.mutants=true"