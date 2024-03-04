package net.il

object IngenDefaults {
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
                      "PATH": "${System.getProperty("user.home")}/.pyenv/shims/python"
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
                  "tag": "simple echo command",
                  "alias": "",
                  "directory": "",
                  "escape": null,
                  "typeCode": 0,
                  "pathCode": 2
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
                  "pathCode": 3
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
              },
              {
                "id": 3,
                "command": {
                  "tag": "",
                  "alias": "test",
                  "directory": "test",
                  "escape": "",
                  "typeCode": 1,
                  "pathCode": 1
                }
              },
              {
                "id": 4,
                "command": {
                  "tag": "",
                  "alias": "test",
                  "directory": "test",
                  "escape": "",
                  "typeCode": 1,
                  "pathCode": 1
                }
              },
              {
                "id": 5,
                "command": {
                  "tag": "",
                  "alias": "test",
                  "directory": "test",
                  "escape": "",
                  "typeCode": 1,
                  "pathCode": 1
                }
              },
              {
                "id": 6,
                "command": {
                  "tag": "",
                  "alias": "test",
                  "directory": "test",
                  "escape": "",
                  "typeCode": 1,
                  "pathCode": 1
                }
              },
              {
                "id": 7,
                "command": {
                  "tag": "",
                  "alias": "test",
                  "directory": "test",
                  "escape": "",
                  "typeCode": 1,
                  "pathCode": 1
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
}