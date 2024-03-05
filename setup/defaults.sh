#!/usr/bin/env bash

CONFIG=$(cat <<-END
{
  "PATH_MAP": {
    "SHELL": {
      "CODE": 0,
      "PATH": "/bin/bash"
    },
    "ECHO": {
      "CODE": 1,
      "PATH": "/bin/echo"
    },
    "PYTHON": {
      "CODE": 2,
      "PATH": "$(which python)"
    }
  },
  "RUNTIME_DIR": "${HOME}/.ingen",
  "ENV": {
    "GDM_SCALE": "1.5"
  }
}
END

)

COMMANDS=$(cat <<-END
[
  {
    "id": 0,
    "command": {
      "tag": "simple echo command",
      "alias": "",
      "directory": "",
      "escape": null,
      "typeCode": 0,
      "pathCode": 1
    }
  },
  {
    "id": 1,
    "command": {
      "tag": "simple interactive python script",
      "alias": "module/input_test.py",
      "directory": "",
      "escape": "xx",
      "typeCode": 2,
      "pathCode": 2
    }
  },
  {
    "id": 2,
    "command": {
      "tag": "simple FIFO async emitter",
      "alias": "./module/mock_async_emitter",
      "directory": "",
      "escape": "",
      "typeCode": 1,
      "pathCode": 1
    }
  }
]
END

)

export CONFIG
export COMMANDS