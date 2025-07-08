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
                print(message, flush=True)

            sys.exit(0)

    def create_message(self, content):
        msg = {"type": "NOTIFICATION", "content": content}
        msg_json = json.dumps(msg)
        return msg_json


if __name__ == "__main__":
    emitter = Emitter()
    emitter.run()
