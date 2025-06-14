import json
import sys
import time
import asyncio


class Emitter:
    def __init__(self):
        self.count = 0

    async def send(self):
        message = self.create_message()
        print(message)
        self.count += 1


    def create_message(self):
        msg = {"type": "NOTIFICATION", "content": str(self.count)}
        msg_json = json.dumps(msg)
        return msg_json

async def main():
    emit = Emitter()
    while emit.count <= 4:
        time.sleep(1)
        await emit.send()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        # TODO: insert some terminate signal to send back to hypervisor/caller
        exit(0)
