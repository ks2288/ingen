import sys
import os
import time

if __name__ == "__main__":
    if len(sys.argv) < 1:
        print("Usage: [python exec path] test_file_writer.py [watch directory path]", flush=True)
    else:
        paths = []
        dir = sys.argv[1]
        count = 0
        while count < 5:
            fp = dir + "/" + str(count) + ".txt"
            paths.append(fp)
            with open(fp, "a") as f:
            # Get the current time in seconds since the epoch (float)
                seconds_since_epoch = time.time()
                # Convert seconds to milliseconds and cast to an integer
                milliseconds = int(round(seconds_since_epoch * 1000))
                f.write(f"Current time in milliseconds: {milliseconds}\n")
            logStr = "File added to: " + dir
            print(logStr, flush=True)
            count += 1
            time.sleep(1)
        print("Test files created. Cleaning up...", flush=True)
        for fpath in paths:
            os.remove(fpath)
        print("Test cleanup complete!", flush=True)