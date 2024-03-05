#!/usr/bin/env bash
# Declare properties here
RUNTIME_DIR="$HOME/.ingen"
CONFIG_DIR="$RUNTIME_DIR/config"
LOG_DIR="$RUNTIME_DIR/log"
MODULES=("python" "c" "sh")
MODULES_DIR="$RUNTIME_DIR/modules"

# Then, export them here for use in init.sh
export RUNTIME_DIR
export CONFIG_DIR
export LOG_DIR
export MODULES
export MODULES_DIR