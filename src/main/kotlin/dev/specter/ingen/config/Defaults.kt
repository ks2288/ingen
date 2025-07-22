package dev.specter.ingen.config

import java.nio.file.Path
import java.nio.file.Paths

object IngenDefaults {
    val CMD_PATH: Path = Paths.get("${IngenConfig.INGEN_CONFIG_DIR}/commands.json")
    val CONFIG_PATH: Path = Paths.get("${IngenConfig.INGEN_CONFIG_DIR}/ingen.json")
    val MODULE_1_PATH: Path = Paths.get("${IngenConfig.INGEN_MODULE_DIR}/input_test.py")
    val MODULE_2_PATH: Path = Paths.get("${IngenConfig.INGEN_MODULE_DIR}/async_echo.sh")
    val MODULE_3_PATH: Path = Paths.get("${IngenConfig.INGEN_MODULE_DIR}/async_echo.py")

    val DEFAULT_CONFIG: String
        get() {
            return """
                {
                  "PATH_MAP": {
                    "SHELL": {
                      "CODE": 0,
                      "PATH": "/bin/bash"
                    },
                    "EXEC": {
                      "CODE": 1,
                      "PATH": ""
                    },
                    "ECHO": {
                      "CODE": 2,
                      "PATH": "/bin/echo"
                    },
                    "PYTHON": {
                      "CODE": 3,
                      "PATH": "/usr/bin/python"
                    },
                    "BTCTL": {
                      "CODE": 4,
                      "PATH": "/usr/bin/bluetoothctl"
                    }
                  },
                  "RUNTIME_DIR": "${System.getProperty("user.home")}/.ingen",
                  "ENV": {
                    "GDM_SCALE": "1.5"
                  }
                }
            """.trimIndent()
        }


    val DEFAULT_COMMANDS: String
        get() = """
            [
              {
                "id": 0,
                "command": {
                  "pcode": 2,
                  "tcode": 0,
                  "alias": "",
                  "dir": "",
                  "esc": null,
                  "desc": "echo using /usr/bin/echo"
                }
              },
              {
                "id": 1,
                "command": {
                  "pcode": 3,
                  "tcode": 2,
                  "alias": "module/input_tester.py",
                  "dir": "",
                  "escape": "xx",
                  "desc": "simple interactive python script"
                }
              },
              {
                "id": 2,
                "command": {
                  "pcode": 0,
                  "tcode": 1,
                  "alias": "module/async_echo.sh",
                  "dir": "",
                  "esc": "",
                  "desc": "simple FIFO async emitter bash script"
                }
              },
              {
                "id": 3,
                "command": {
                  "pcode": 3,
                  "tcode": 1,
                  "alias": "module/async_echo.py",
                  "dir": "",
                  "esc": "",
                  "desc": "simple FIFO async emitter python script"
                }
              }
            ]
        """.trimIndent()
}

object ScriptDefaults {
    val INTERACTIVE_PY_TEST_SCRIPT: String
        get() = """
            import asyncio
            import sys
            
            
            tester = None
            
            class InputTester:
                def __init__(self):
                    pass
            
                async def capture(self):
                    while True:
                        i = input()
                        if str(i) == "xx":
                            break
                        sys.stdout.write(str(i) + "\n")
                        sys.stdout.flush()
                
                async def run(self):
                    await self.capture()
                    self.stop()
            
            
                def stop(self):
                    sys.stdout.close()
                    sys.exit(0)
            
            
            if __name__ == "__main__":
                try:
                    tester = InputTester()
                    asyncio.run(tester.run())
                except KeyboardInterrupt or InterruptedError:
                    tester.stop()
        """.trimIndent()

    val ASYNC_EMITTER_SH_TEST_SCRIPT: String
        get() {
            val esc = "\$i"
            return """
                #!/usr/bin/env bash
        
                for i in {1..10}
                do
                  echo "test $esc"
                  sleep 1
                done
            """.trimIndent()
        }

    val ASYNC_EMITTER_PY_TEST_SCRIPT: String
        get() = """
            import json
            import sys
            import time


            class Emitter:
                def __init__(self):
                    pass

                def run(self):
                    while True:
                        time.sleep(2)
                        count = 0
                        while count <= 4:
                            time.sleep(1)
                            count += 1
                            message = self.create_message([str(count)])
                            print(message)
                        sys.exit(0)

                def create_message(self, content):
                    msg = {"type": "NOTIFICATION", "content": content}
                    msg_json = json.dumps(msg)
                    return msg_json


            if __name__ == "__main__":
                emitter = Emitter()
                emitter.run()

        """.trimIndent()
}
