#!/usr/bin/env bash

. ./configuration.sh

echo "
Ingen setup initialized...

Creating runtime directories at $RUNTIME_DIR

"
mkdir "$RUNTIME_DIR"
mkdir "$LOG_DIR"

