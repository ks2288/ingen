import asyncio
import sys

class SensorReading:
    def __init__(self, temp, pressure, humidity):
        self.temp = temp
        self.pressure = pressure
        self.humidity = humidity

    # TODO: morph this into conformance with the venom spec
    def to_separated_string(self):
        msgString = str(self.temp)
        msgString += chr(29)
        msgString += str(self.pressure)
        msgString += chr(29)
        msgString += str(self.humidity)
        return msgString


class SensorService:
    def __init__(self):
        self.mTemp = 30.0
        self.mPressure = 995.0
        self.mHumidity = 45.0
        self.increment = True


    async def read_sensors(self):
        td = 0.0
        pd = 0.0
        hd = 0.0
        if (self.increment):
            td += 2.4
            pd += 3.3
            hd += 4.2
        else:
            td -= 2.4
            pd -= 3.3
            hd -= 4.2
        self.mTemp += td
        self.mPressure += pd
        self.mHumidity += hd

        reading = SensorReading(temp=self.mTemp, pressure=self.mPressure, humidity=self.mHumidity)
        if (self.increment):
            self.increment = False
        else:
            self.increment = True
        return reading.to_separated_string()


async def main(interval):
    svc = SensorService()
    while True:
        reading = await svc.read_sensors()
        print(reading, flush=True)
        await asyncio.sleep(int(interval))


if __name__ == "__main__":
    if len(sys.argv) < 1:
        print("Usage: [python exec path] sense-async.py [update interval (s)]")
    else:
        inter = sys.argv[1]
        try:
            asyncio.run(main(inter))
        except KeyboardInterrupt:
            # TODO: insert some terminate signal to send back to hypervisor/caller
            exit(0)
