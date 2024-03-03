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