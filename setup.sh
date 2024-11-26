#!/usr/bin/env bash

echo "
*** Creating Ingen directories... ***
"

INGEN_WORK_DIR="$HOME/.ingen"
INGEN_CONFIG_DIR="$INGEN_WORK_DIR/config"
INGEN_TEST_DIR="$INGEN_WORK_DIR/test"
LOG_DIR_PATH="$INGEN_WORK_DIR/log"
COMMANDS_TEMPLATE_PATH="src/main/resources/template/commands.json"
CONFIG_TEMPLATE_PATH="src/main/resources/template/ingen.json"

if [ ! -d "$INGEN_CONFIG_DIR" ]; then
  mkdir -p "$INGEN_CONFIG_DIR"
fi

if [ ! -d "$INGEN_TEST_DIR" ]; then
  mkdir "$INGEN_TEST_DIR"
fi
if [ ! -d "$LOG_DIR_PATH" ]; then
  mkdir "$LOG_DIR_PATH"
fi

echo "*** Ingen directories created! ***

*** Copying configuration and command file templates... ***"

if [ ! -f "$COMMANDS_TEMPLATE_PATH" ]; then
  cp $COMMANDS_TEMPLATE_PATH "$INGEN_CONFIG_DIR"
fi

if [ ! -f "$CONFIG_TEMPLATE_PATH" ]; then
  cp $CONFIG_TEMPLATE_PATH "$INGEN_CONFIG_DIR"
fi


echo "
*** Template files copied! ***bash src/main/resources/shell/setup.sh

*** Ingen setup completed successfully! ***

******

Next Steps:
1. Refer to the README for information on usage specifics.
2. The command JSON spec file is located at ~/.ingen/config/commands.json; edit/add to it according to your needs.
3. The config JSON spec file is located at ~/.ingen/config/config.json; edit/add this file as well according to your needs.

******
"